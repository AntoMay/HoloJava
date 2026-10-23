package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Drives the production stage-completion and stage-unlock paths and proves the four properties the
 * progression depends on:
 *
 * <ol>
 *   <li><b>Completion</b> — a won run marks the stage complete.</li>
 *   <li><b>Unlock</b> — the next stage opens according to the reference's unlock table, which is
 *       requirement-driven and <em>not</em> sequential.</li>
 *   <li><b>Idempotence</b> — finishing the same stage again changes nothing.</li>
 *   <li><b>Persistence</b> — progress survives a save/reload cycle and older saves still load.</li>
 * </ol>
 *
 * <p>The methods exercised are the real ones: {@code FullGameCanvas.handleRunEnd} is the body the
 * game loop calls the moment a run ends, and {@code stageUnlocked} is the gate the stage select
 * screen consults. Nothing here re-implements that logic.
 *
 * <p><b>Construction.</b> {@code FullGameCanvas} extends {@code Canvas} and cannot be constructed
 * on a desktop JVM, and {@code update()} calls the MIDP {@code getKeyStates()}, whose stub throws.
 * Instances of both canvas and world are therefore allocated without running their constructors and
 * driven field by field through {@code sun.misc.Unsafe}, which is also required because several
 * fields are final. Test-only: no file under {@code src/} references {@code sun.*}.
 */
public final class StageCompletionUnlockTest {

    private static int checks = 0;
    private static int failures = 0;

    // ---------------------------------------------------------------- unsafe plumbing

    private static Object unsafe;

    static {
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("sun.misc.Unsafe unavailable", e);
        }
    }

    private static Object allocate(Class<?> type) {
        try {
            return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (Exception e) {
            throw new IllegalStateException("cannot allocate " + type.getName(), e);
        }
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            long offset = (Long) unsafe.getClass()
                    .getMethod("objectFieldOffset", Field.class).invoke(unsafe, f);
            Class<?> t = f.getType();
            if (t == int.class) {
                unsafe.getClass().getMethod("putInt", Object.class, long.class, int.class)
                        .invoke(unsafe, target, offset, value);
            } else if (t == long.class) {
                unsafe.getClass().getMethod("putLong", Object.class, long.class, long.class)
                        .invoke(unsafe, target, offset, value);
            } else if (t == boolean.class) {
                unsafe.getClass().getMethod("putBoolean", Object.class, long.class, boolean.class)
                        .invoke(unsafe, target, offset, value);
            } else {
                unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                        .invoke(unsafe, target, offset, value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }

    private static int getInt(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static boolean getBool(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getBoolean(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static final class Recording implements SaveStore.RecordStorage {
        byte[] record;
        int writes;

        Recording(byte[] initial) {
            record = initial;
        }

        public byte[] read(int recordId) {
            return record == null ? null : record.clone();
        }

        public boolean write(int recordId, byte[] payload) {
            writes++;
            record = payload.clone();
            return true;
        }
    }

    private static Class<?> canvasClass() throws Exception {
        return Class.forName("me.holojava.fullgame.FullGameCanvas");
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    /** A world parked in the given terminal state. */
    private static Object worldIn(int state) throws Exception {
        Object w = allocate(worldClass());
        set(w, "state", Integer.valueOf(state));
        return w;
    }

    /** A canvas bound to a save and a finished run, ready for handleRunEnd(). */
    private static Object canvas(SaveStore save, int worldState, int modeIndex,
            int characterIndex, int stageIndex) throws Exception {
        Object c = allocate(canvasClass());
        set(c, "save", save);
        set(c, "assets", null);
        set(c, "shutdownRequest", null);
        set(c, "world", worldIn(worldState));
        set(c, "modeIndex", Integer.valueOf(modeIndex));
        set(c, "characterIndex", Integer.valueOf(characterIndex));
        set(c, "stageIndex", Integer.valueOf(stageIndex));
        set(c, "screen", Integer.valueOf(0));
        set(c, "resultIndex", Integer.valueOf(0));
        set(c, "worldWasVictory", Boolean.FALSE);
        return c;
    }

    private static void handleRunEnd(Object canvas) throws Exception {
        Method m = canvasClass().getDeclaredMethod("handleRunEnd");
        m.setAccessible(true);
        m.invoke(canvas);
    }

    private static boolean stageUnlocked(Object canvas, int stageIndex) throws Exception {
        Method m = canvasClass().getDeclaredMethod("stageUnlocked", int.class);
        m.setAccessible(true);
        return ((Boolean) m.invoke(canvas, Integer.valueOf(stageIndex))).booleanValue();
    }

    private static int stageCount() {
        return ContentCatalog.STAGES.length;
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testUnlockTableMatchesTheReference();
        testVictoryRecordsCompletion();
        testDefeatRecordsNoCompletion();
        testCompletionUnlocksTheGatedStage();
        testCompletionIsIdempotent();
        testProgressSurvivesReload();
        testLegacySavesStillLoadAndUnlock();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /**
     * The unlock table itself, checked against values read from the reference JAR's
     * {@code ContentCatalog} bytecode.
     *
     * <p>Note the shape: stage 5 (Grassy Plains Night) requires order <b>1</b>, not order 4. The
     * progression is requirement-driven, so assuming a chain would be wrong. Fantasy Island is
     * {@code 99}, which {@code stageUnlocked} treats exactly like {@code -1}: always available.
     *
     * <p>The trailing {@code 99} is Arena Hutan Sawit, this project's own stage rather than a
     * reference one, so its entry is not bytecode-derived. It is also currently always available:
     * it was gated behind Fantasy Island (order 4) until it was opened up for playtesting. Both
     * changes are single literals in {@code ContentCatalog.STAGES}; the comment there records the
     * intended value.
     */
    private static void testUnlockTableMatchesTheReference() {
        System.out.println("[1] the unlock table matches the reference");
        int[] expected = { -1, 0, 1, 2, 99, 1, 5, 6, 7, 2, 99 };
        for (int i = 0; i < expected.length && i < stageCount(); i++) {
            expectEq(ContentCatalog.stage(i).unlockRequirement, expected[i],
                "stage " + i + " (" + ContentCatalog.stage(i).id + ") unlockRequirement");
            // Completion is recorded under .order while the unlock table is keyed by order too.
            // Those two formulations coincide only while every stage's order equals its index, so
            // that coincidence is asserted explicitly: if the catalog is ever reordered, this
            // fails and the difference between recording an index and recording an order becomes
            // load-bearing rather than invisible.
            expectEq(ContentCatalog.stage(i).order, i,
                "stage " + i + " (" + ContentCatalog.stage(i).id + ") order equals its index");
        }
    }

    /** Clause 1. A won run in Stage Mode marks the stage complete under its order, not its index. */
    private static void testVictoryRecordsCompletion() throws Exception {
        System.out.println("[2] a victory records completion");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = canvas(save, RunWorld.STATE_VICTORY, 0, 0, 1);   // stage index 1, order 1

        handleRunEnd(c);

        expect(save.isCompleted(0, ContentCatalog.stage(1).order), "the stage is marked complete");
        expect(getBool(c, "worldWasVictory"), "the run is recorded as a victory");
        expectEq(getInt(c, "screen"), ResultsScreen(), "the canvas moved to the results screen");
        expectEq(storage.writes, 1, "exactly one record write was issued");
    }

    private static int ResultsScreen() throws Exception {
        Field f = canvasClass().getDeclaredField("RESULTS");
        f.setAccessible(true);
        return f.getInt(null);
    }

    /** A lost run must not mark anything complete. */
    private static void testDefeatRecordsNoCompletion() throws Exception {
        System.out.println("[3] a defeat records no completion");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = canvas(save, RunWorld.STATE_GAME_OVER, 0, 0, 1);

        handleRunEnd(c);

        expect(!save.isCompleted(0, ContentCatalog.stage(1).order), "nothing was marked complete");
        expect(!getBool(c, "worldWasVictory"), "the run is not recorded as a victory");
        expectEq(storage.writes, 0, "no record write was issued");
        expectEq(getInt(c, "screen"), ResultsScreen(), "the canvas still moved to results");
    }

    /**
     * Clause 2. Stage 2 (Holo Office) is gated on order 0. Clearing stage 1 must open it, and the
     * gate must stay shut before that.
     */
    private static void testCompletionUnlocksTheGatedStage() throws Exception {
        System.out.println("[4] completion unlocks the gated stage");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = canvas(save, RunWorld.STATE_RUNNING, 0, 0, 0);

        expect(!stageUnlocked(c, 1), "stage 2 is locked before stage 1 is cleared");
        expect(stageUnlocked(c, 0), "stage 1 is unlocked from the start");

        // Clear stage 1 for character 0.
        handleRunEnd(canvas(save, RunWorld.STATE_VICTORY, 0, 0, 0));
        expect(stageUnlocked(c, 1), "stage 2 opened once stage 1 was cleared");

        // A different character now shares the same progress and can access the unlocked stage.
        Object other = canvas(save, RunWorld.STATE_RUNNING, 0, 1, 0);
        expect(stageUnlocked(other, 1), "character 1 also has stage 2 unlocked (shared progress)");
    }

    /**
     * Clause 3. Replaying a cleared stage must not corrupt progress. Completion is recorded by
     * setting a bit, so a repeat is a no-op by construction — this proves it.
     */
    private static void testCompletionIsIdempotent() throws Exception {
        System.out.println("[5] completion is idempotent");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = canvas(save, RunWorld.STATE_VICTORY, 0, 0, 1);

        handleRunEnd(c);
        byte[] afterFirst = storage.record.clone();
        int writesAfterFirst = storage.writes;

        for (int i = 0; i < 3; i++) handleRunEnd(c);

        expectEq(storage.writes, writesAfterFirst + 3,
            "each replay re-saves the same value rather than skipping");
        boolean identical = true;
        for (int i = 0; i < afterFirst.length; i++) {
            if (storage.record[i] != afterFirst[i]) identical = false;
        }
        expect(identical, "the stored record is byte-for-byte unchanged by replays");
        expect(save.isCompleted(0, 1), "the stage is still marked complete");
        expect(stageUnlocked(c, 2), "the stage gated on order 1 is still open");
    }

    /** Clause 4. Reloading the record must preserve progress and unlock state. */
    private static void testProgressSurvivesReload() throws Exception {
        System.out.println("[6] progress survives a reload");
        Recording storage = new Recording(new byte[56]);
        SaveStore first = new SaveStore(storage);
        Object c = canvas(first, RunWorld.STATE_VICTORY, 0, 0, 2);   // stage index 2, order 2
        handleRunEnd(c);

        // A fresh session reading the same bytes.
        SaveStore reloaded = new SaveStore(new Recording(storage.record.clone()));
        expect(reloaded.isCompleted(0, 2), "the cleared stage survived the reload");
        expect(!reloaded.isCompleted(0, 3), "and nothing else was granted");
        expect(reloaded.coinBalance() == 0L, "the coin field is untouched by completion");

        Object after = canvas(reloaded, RunWorld.STATE_RUNNING, 0, 0, 0);
        expect(stageUnlocked(after, 3), "the stage gated on order 2 is open after reload");
        expect(stageUnlocked(after, 4), "Fantasy Island is open regardless, per unlock 99");
    }

    /** Clause 4, compatibility: a pre-coin 48-byte save must still load and still unlock. */
    private static void testLegacySavesStillLoadAndUnlock() throws Exception {
        System.out.println("[7] a legacy 48-byte save still loads and unlocks");
        byte[] legacy = new byte[48];
        legacy[0] = (byte) ((1 << 0) | (1 << 2));      // character 0 cleared orders 0 and 2
        legacy[47] = 0x41;

        SaveStore save = new SaveStore(new Recording(legacy.clone()));
        expect(save.isCompleted(0, 0), "order 0 came from the legacy record");
        expect(save.isCompleted(0, 2), "order 2 came from the legacy record");
        expect(!save.isCompleted(0, 1), "order 1 was not set");

        Object c = canvas(save, RunWorld.STATE_RUNNING, 0, 0, 0);
        expect(stageUnlocked(c, 1), "order 0 is satisfied, so stage 2 is open");
        expect(stageUnlocked(c, 3), "order 2 is satisfied, so stage 4 is open");
        expect(!stageUnlocked(c, 2), "order 1 is missing, so stage 3 stays shut");

        // And a fresh completion on a legacy save upgrades it without losing the old bits.
        Object c2 = canvas(save, RunWorld.STATE_VICTORY, 0, 0, 1);
        handleRunEnd(c2);
        expect(save.isCompleted(0, 1), "the new completion was added");
        expect(save.isCompleted(0, 0) && save.isCompleted(0, 2), "the legacy bits survived");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}