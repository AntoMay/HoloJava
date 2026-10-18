package me.holojava.fullgame;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the original Arena Hutan Sawit stage: its enemies Biji Sawit and 19 Kucing, its bosses
 * WoWo (10:00) and FufuFafa (20:00), the palm_forest tile, the unlock gate, and the save
 * round-trip for its completion bit.
 *
 * <p>No game logic is re-implemented here. The unlock gate is the real
 * {@code FullGameCanvas.stageUnlocked}, the boss director is the real
 * {@code RunWorld.updateBossDirector} and {@code spawnBossById}, and persistence is the real
 * {@code SaveStore}. {@code Canvas} and {@code RunWorld} cannot be constructed on a desktop JVM and
 * several of their fields are final, so instances are allocated with {@code sun.misc.Unsafe} and
 * driven field by field, exactly as the sibling suites do.
 *
 * <p>The stage, its enemies, its bosses and its art are this project's own design, not recovered
 * from the reference game. Every value here is therefore an original choice, and the tests pin the
 * shape of those choices rather than any claim about the original game.
 */
public final class ArenaHutanSawitTest {

    private static int checks = 0;
    private static int failures = 0;

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

    // ---------------------------------------------------------------- plumbing

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
            } else if (t == double.class) {
                unsafe.getClass().getMethod("putDouble", Object.class, long.class, double.class)
                        .invoke(unsafe, target, offset, value);
            } else {
                unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                        .invoke(unsafe, target, offset, value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }

    private static Object get(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }

    private static int getInt(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getInt(target);
    }

    private static boolean getBool(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getBoolean(target);
    }

    private static Object filled(Class<?> component, int count) throws Exception {
        Object array = Array.newInstance(component, count);
        for (int i = 0; i < count; i++) {
            Object element = allocate(component);
            try {
                Field active = component.getDeclaredField("active");
                active.setAccessible(true);
                active.setBoolean(element, false);
            } catch (NoSuchFieldException ignored) {
                // no flag to default
            }
            Array.set(array, i, element);
        }
        return array;
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(String got, String want, String label) {
        expect(want.equals(got), label + ": expected " + want + " got " + got);
    }

    private static boolean isPng(File png) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(png);
            byte[] magic = new byte[8];
            int n = 0;
            while (n < 8) {
                int r = in.read(magic, n, 8 - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            return n == 8 && (magic[0] & 0xFF) == 0x89 && magic[1] == 0x50
                && magic[2] == 0x4E && magic[3] == 0x47;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static final class Recording implements SaveStore.RecordStorage {
        byte[] record;
        Recording(byte[] initial) { record = initial; }
        public byte[] read(int recordId) { return record == null ? null : record.clone(); }
        public boolean write(int recordId, byte[] payload) { record = payload.clone(); return true; }
    }

    private static Class<?> canvasClass() throws Exception {
        return Class.forName("me.holojava.fullgame.FullGameCanvas");
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    /** The catalog index of the stage under test, or -1 when it is missing. */
    private static int stageIndex() {
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            if ("stage6".equals(ContentCatalog.STAGES[i].id)) return i;
        }
        return -1;
    }

    private static boolean stageUnlocked(SaveStore save, int stageIndex, int characterIndex)
            throws Exception {
        Object c = allocate(canvasClass());
        set(c, "save", save);
        set(c, "assets", null);
        set(c, "characterIndex", Integer.valueOf(characterIndex));
        Method m = canvasClass().getDeclaredMethod("stageUnlocked", int.class);
        m.setAccessible(true);
        return ((Boolean) m.invoke(c, Integer.valueOf(stageIndex))).booleanValue();
    }

    /**
     * A RunWorld bound to the stage under test, with the pools the boss director and spawner
     * touch. startTime is 0 so a given {@code now} argument reads directly as "elapsed seconds".
     */
    private static Object world() throws Exception {
        int stage = stageIndex();
        Object w = allocate(worldClass());
        set(w, "stage", ContentCatalog.STAGES[stage]);
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "stageIndex", Integer.valueOf(stage));
        set(w, "save", null);
        set(w, "startTime", Long.valueOf(0L));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "bossEncounterIndex", Integer.valueOf(-1));
        set(w, "nextEndlessEncounter", Integer.valueOf(0));
        set(w, "state", Integer.valueOf(RunWorld.STATE_RUNNING));
        set(w, "scaleA", Integer.valueOf(0));
        set(w, "scaleB", Integer.valueOf(0));
        set(w, "encounterStarted", new boolean[4]);
        set(w, "encounterDefeated", new boolean[4]);
        set(w, "pickups", filled(Class.forName("me.holojava.fullgame.RunWorld$Pickup"), 96));
        set(w, "enemies", filled(Class.forName("me.holojava.fullgame.RunWorld$Enemy"), 48));
        set(w, "shots", filled(Class.forName("me.holojava.fullgame.RunWorld$Shot"), 128));
        set(w, "boss", filledBoss());
        return w;
    }

    private static Object filledBoss() throws Exception {
        Class<?> type = Class.forName("me.holojava.fullgame.RunWorld$Boss");
        Object boss = allocate(type);
        set(boss, "active", Boolean.FALSE);
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "x", Integer.valueOf(100));
        set(boss, "y", Integer.valueOf(100));
        set(boss, "hp", Integer.valueOf(10));
        set(boss, "maxHp", Integer.valueOf(10));
        set(boss, "anim", Integer.valueOf(0));
        set(boss, "hitMask", Long.valueOf(0L));
        set(boss, "chargeUntil", Long.valueOf(0L));
        return boss;
    }

    private static Object boss(Object w) throws Exception {
        return get(w, "boss");
    }

    private static void director(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateBossDirector", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testCatalogEntry();
        testEnemyDefinition();
        testBossDefinition();
        testBossSchedule();
        testUnlockGate();
        testCompletionRoundTrip();
        testArtOnDisk();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** The stage exists, is reachable from Stage Mode, and names its own art. */
    private static void testCatalogEntry() {
        System.out.println("[1] the stage is defined and selectable");
        int i = stageIndex();
        expect(i >= 0, "stage6 is present in the catalog");
        if (i < 0) return;
        ContentCatalog.StageDef s = ContentCatalog.STAGES[i];
        expectEq(s.order, i, "stage order equals its catalog index");
        expect(!s.hard, "it is not a hard variant");
        expect(!s.timeStage, "it is not the Time Mode stage");
        expectEq(s.name, "Arena Hutan Sawit", "the stage name");
        expectEq(s.tile, "palm_forest", "the background tile key");
        expect(s.maxEnemies > 0, "it declares a positive enemy cap");
        expect(s.spawnIntervalMs > 0, "it declares a positive spawn interval");
        expect(ContentCatalog.validate() == null,
            "the catalog still validates with it in place");
    }

    /** Biji Sawit: an orbit charger, nerfed to swarm-safe values, all this project's own numbers. */
    private static void testEnemyDefinition() {
        System.out.println("[2] Biji Sawit and 19 Kucing are the stage's enemies");
        ContentCatalog.EnemyDef e = ContentCatalog.enemy("biji_sawit");
        expect(e != null, "biji_sawit is in the enemy catalog");
        if (e == null) return;
        expectEq(e.name, "Biji Sawit", "the enemy name");
        expectEq(e.behavior, ContentCatalog.AI_ORBIT_CHARGER,
            "the behavior circles before it darts, unlike a plain charger");
        expect(e.behavior != ContentCatalog.AI_CHARGER, "it is not the beeline charger");
        expect(e.hp > 0 && e.atk > 0, "it has positive HP and ATK");
        // Nerf pins: the swarm fills the arena, so each enemy has to stay under the reference
        // chargers the catalog already carries on both pressure axes.
        ContentCatalog.EnemyDef matsurisu = ContentCatalog.enemy("matsurisu");
        expect(e.atk < matsurisu.atk, "it hits softer than the earliest documented charger");
        expect(e.speed > 0.4 && e.speed < 0.55,
            "it moves slower than a plain chaser's pace of the first pass");
        expect(e.exp > 0, "it awards experience");
        expect(e.coinChance > 0, "it can drop coins");
        expectEq(e.sprite, "BijiSawit", "the sprite directory");
        expect(!e.documentedBaseStats,
            "its stats are marked as this project's own, not read off the reference game");

        // 19 Kucing is the second Hutan Sawit enemy: a plain chaser, so the swarm gains a second
        // silhouette without a second circling wind-up to read on screen. Mid-table stats keep
        // the stage's pressure profile roughly where Biji Sawit alone left it.
        ContentCatalog.EnemyDef kucing = ContentCatalog.enemy("19kucing");
        expect(kucing != null, "19kucing is in the enemy catalog");
        if (kucing != null) {
            expectEq(kucing.name, "19 Kucing", "the second enemy's name");
            expectEq(kucing.behavior, ContentCatalog.AI_CHASER,
                "it walks straight at the player");
            expectEq(kucing.sprite, "Kucing19", "the sprite directory");
            expect(kucing.hp > 0 && kucing.atk > 0 && kucing.speed > 0, "it has real stats");
            expect(kucing.atk <= e.atk + 2, "it stays inside the swarm's per-enemy pressure");
            expect(!kucing.documentedBaseStats,
                "its stats are marked as this project's own too");
        }

        ContentCatalog.StageDef s = ContentCatalog.STAGES[stageIndex()];
        expect("biji_sawit".equals(s.enemyA) && "19kucing".equals(s.enemyB)
            && "biji_sawit".equals(s.enemyC),
            "the stage mixes Biji Sawit over two slots with 19 Kucing in the middle one");
    }

    /** WoWo: tankier than every non-Hard boss in the catalog, and never softer than the weakest. */
    private static void testBossDefinition() {
        System.out.println("[3] WoWo is a defined radial shooter");
        ContentCatalog.BossDef b = ContentCatalog.boss("wowo");
        expect(b != null, "wowo is in the boss catalog");
        if (b == null) return;
        expectEq(b.name, "WoWo", "the boss name");
        expectEq(b.behavior, ContentCatalog.AI_SHOOTER, "the behavior");
        expect(b.hp > 0 && b.atk > 0, "it has positive HP and ATK");
        expect(b.exp > 0, "it awards experience");
        expectEq(b.sprite, "WoWo", "the sprite directory");
        expect(!b.assetApproximation,
            "it is marked as original art rather than a borrowed fallback frame");

        // WoWo has to be a real step up from the final boss of the stage it unlocks
        // behind, or the new stage would feel like a step back.
        ContentCatalog.BossDef pekodam = ContentCatalog.boss("pekodam");
        expect(b.hp > pekodam.hp, "it outlasts the previous stage's final boss on HP");
        expect(b.atk > pekodam.atk, "and it hits harder too");

        ContentCatalog.StageDef s = ContentCatalog.STAGES[stageIndex()];
        expectEq(s.bossA, "wowo", "the 10:00 encounter");
        expectEq(s.bossB, "fufufafa", "the 20:00 final encounter");

        // FufuFafa closes the stage on a fresh fight instead of a repeat WoWo, and it out-ranks
        // the fight before it on both pressure axes.
        ContentCatalog.BossDef fff = ContentCatalog.boss("fufufafa");
        expect(fff != null, "fufufafa is in the boss catalog");
        if (fff != null) {
            expectEq(fff.name, "FufuFafa", "the final boss's name");
            expectEq(fff.behavior, ContentCatalog.AI_SHOOTER, "the radial-burst behavior");
            expectEq(fff.sprite, "FufuFafa", "the sprite directory");
            expect(!fff.assetApproximation,
                "it is marked as original art rather than a borrowed fallback frame");
            expect(fff.hp > b.hp, "it outlasts WoWo on HP");
            expect(fff.atk > b.atk, "and it hits harder too");
        }
    }

    /** Both scheduled encounters actually fire through the real director, at the catalog's times. */
    private static void testBossSchedule() throws Exception {
        System.out.println("[4] the boss director schedules WoWo, then FufuFafa");
        ContentCatalog.StageDef s = ContentCatalog.STAGES[stageIndex()];
        expectEq(s.bossATimeSec, 600, "the first WoWo is scheduled for 10:00");
        expectEq(s.bossBTimeSec, 1200, "the final FufuFafa is scheduled for 20:00");

        int wowoIndex = -1;
        for (int i = 0; i < ContentCatalog.BOSSES.length; i++) {
            if ("wowo".equals(ContentCatalog.BOSSES[i].id)) wowoIndex = i;
        }
        expect(wowoIndex >= 0, "wowo has a boss index");
        int fufufafaIndex = -1;
        for (int i = 0; i < ContentCatalog.BOSSES.length; i++) {
            if ("fufufafa".equals(ContentCatalog.BOSSES[i].id)) fufufafaIndex = i;
        }
        expect(fufufafaIndex >= 0, "fufufafa has a boss index");

        Object early = world();
        director(early, 599_000L);
        expect(!getBool(boss(early), "active"), "no boss a second before the 10:00 gate");

        Object first = world();
        director(first, 600_000L);
        expect(getBool(boss(first), "active"), "WoWo appears on the 10:00 gate");
        expectEq(getInt(boss(first), "defIndex"), wowoIndex, "and it is WoWo");
        expect(getInt(boss(first), "maxHp") > 0, "with a positive HP pool");
        expectEq(getInt(first, "bossEncounterIndex"), 0, "recorded as encounter 0");

        Object final_ = world();
        // Reach 20:00 with the 10:00 encounter already fought, which is the state the real
        // director would be in: encounter 0 is marked started, so it falls through to slot 1.
        boolean[] started = (boolean[]) get(final_, "encounterStarted");
        started[0] = true;
        set(final_, "encounterStarted", started);
        set(final_, "encounterDefeated", new boolean[] { true, false, false, false });
        director(final_, 1200_000L);
        expect(getBool(boss(final_), "active"), "FufuFafa appears on the 20:00 gate");
        expectEq(getInt(boss(final_), "defIndex"), fufufafaIndex, "and it is FufuFafa");
        expectEq(getInt(final_, "bossEncounterIndex"), 1, "recorded as encounter 1, the final one");
    }

    /**
     * The stage is marked always available, the same {@code 99} marker Fantasy Island uses and the
     * one {@code FullGameCanvas.stageUnlocked} treats exactly like {@code -1}. The gate was
     * originally "clear Fantasy Island first" (order 4); it was opened for playtesting, so this
     * asserts the marker and that the real gate method honours it on a completely empty save.
     */
    private static void testUnlockGate() throws Exception {
        System.out.println("[5] the stage is always available");
        int stage = stageIndex();
        expectEq(ContentCatalog.stage(stage).unlockRequirement, 99,
            "the stage declares the always-available marker");

        SaveStore empty = new SaveStore(new Recording(new byte[56]));
        expect(stageUnlocked(empty, stage, 0),
            "available on a fresh save with no progress at all");
        expect(stageUnlocked(empty, stage, 4),
            "and for every character, not just the first");

        // The marker must mean "ignore the save", so bits set or unset must not matter.
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        expect(stageUnlocked(save, stage, 0), "available before any stage is cleared");
        save.setCompleted(0, 4);
        expect(stageUnlocked(save, stage, 0), "and still available after Fantasy Island is cleared");

        // The stage must remain allowed in Stage Mode even though it is not a Time Mode stage.
        expect(!ContentCatalog.stage(stage).timeStage, "it is not a Time Mode stage");
    }

    /**
     * The stage's completion bit lives at order 10, past the eight bits one byte can hold. This
     * proves the widened SaveStore layout records it, that it survives a reload, and that the
     * low-order bits written by earlier builds are neither disturbed nor reinterpreted.
     */
    private static void testCompletionRoundTrip() throws Exception {
        System.out.println("[6] order 10 completion records and survives a reload");
        int order = ContentCatalog.stage(stageIndex()).order;
        expect(order > 7, "the stage order is past the single-byte range");

        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.setCompleted(0, order);
        expect(save.isCompleted(0, order), "the new order is recorded");
        expect(!save.isCompleted(0, 0) && !save.isCompleted(0, 4),
            "and it did not disturb the neighbouring low-order bits");

        SaveStore reloaded = new SaveStore(storage);
        expect(reloaded.isCompleted(0, order), "and it survives a save/reload cycle");

        save.setCompleted(0, 99);
        expect(!save.isCompleted(0, 99), "an out-of-range order is refused");
        expect(!save.isCompleted(0, -1), "a negative order is refused");

        // A record written by a pre-widening build: low bits in the primary byte, high byte clear.
        byte[] oldWidth = new byte[56];
        oldWidth[0] = (byte) (1 | (1 << 3));
        SaveStore old = new SaveStore(new Recording(oldWidth));
        expect(old.isCompleted(0, 0) && old.isCompleted(0, 3),
            "an existing single-byte save still decodes its low-order bits");
        expect(!old.isCompleted(0, 4), "with the untouched bits still clear");
        expect(!old.isCompleted(0, order), "and no phantom high-order bit appears");

        // And a record written by the widened build, read back the same way.
        byte[] newWidth = new byte[56];
        newWidth[0] = (byte) (1 | (1 << 3));
        newWidth[5] = (byte) (1 << (order - 8));
        SaveStore wide = new SaveStore(new Recording(newWidth));
        expect(wide.isCompleted(0, 0) && wide.isCompleted(0, 3),
            "the widened layout keeps the low-order bits readable");
        expect(wide.isCompleted(0, order), "and reads the high-order bit from the second byte");
    }

    /** The generated art is really on disk, with the frame counts the loader expects. */
    private static void testArtOnDisk() {
        System.out.println("[7] the palm forest art is packaged");
        File tile = new File("assets/fullgame/stages/palm_forest.png");
        expect(tile.isFile(), "the palm_forest tile exists (" + tile.getPath() + ")");
        if (tile.isFile()) expect(isPng(tile), "the tile has PNG magic");

        for (int f = 0; f < 3; f++) {
            File png = new File("assets/fullgame/enemies/BijiSawit/spr_BijiSawit_" + f + ".png");
            expect(png.isFile(), "Biji Sawit frame " + f + " exists");
            if (png.isFile()) expect(isPng(png), "Biji Sawit frame " + f + " has PNG magic");
        }
        for (int f = 0; f < 3; f++) {
            File png = new File("assets/fullgame/enemies/Kucing19/spr_Kucing19_" + f + ".png");
            expect(png.isFile(), "19 Kucing frame " + f + " exists");
            if (png.isFile()) expect(isPng(png), "19 Kucing frame " + f + " has PNG magic");
        }
        for (int f = 0; f < 4; f++) {
            File png = new File("assets/fullgame/bosses/WoWo/spr_WoWo_" + f + ".png");
            expect(png.isFile(), "WoWo frame " + f + " exists");
            if (png.isFile()) expect(isPng(png), "WoWo frame " + f + " has PNG magic");
        }
        for (int f = 0; f < 4; f++) {
            File png = new File("assets/fullgame/bosses/FufuFafa/spr_FufuFafa_" + f + ".png");
            expect(png.isFile(), "FufuFafa frame " + f + " exists");
            if (png.isFile()) expect(isPng(png), "FufuFafa frame " + f + " has PNG magic");
        }
    }
}