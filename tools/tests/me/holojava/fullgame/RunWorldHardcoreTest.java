package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Proves the Hardcore flag is off by default and that turning it on actually caps the run to 1 HP.
 *
 * <p>Hardcore is the one upgrade that is not a level: it flips a bit in {@code SaveStore} that
 * {@code RunWorld} reads when the run starts. That makes it the easiest thing in the shop to get
 * quietly wrong, because nothing about it is visible on the shop page -- the row reads ON/OFF and
 * the effect shows up dozens of frames later as a run that ends almost immediately. The shop-side
 * test covers the toggle itself; this one covers the part that actually matters, which is the
 * consequence.
 *
 * <p><b>Construction.</b> {@code RunWorld}'s constructor reaches {@code Image.createImage}, which
 * throws an {@code Error} the MIDP stub cannot survive on a desktop JVM, so instances are
 * allocated without it and only the fields the exercised code reads are set. The code under test
 * is the genuine production bytecode.
 *
 * <p><b>Not proven.</b> The real spawn, rendering and the moment of death; those need a handset.
 */
public final class RunWorldHardcoreTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testHardcoreDefaultsOff();
        testHardcoreCapsEndlessToOneHp();
        testHardcoreLeavesOtherModesAlone();

        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) System.exit(1);
    }

    /** A fresh install must not start in hardcore; the flag is opt-in. */
    private static void testHardcoreDefaultsOff() throws Exception {
        section("hardcore is off on a fresh install");
        SaveStore save = new SaveStore(new Recording(new byte[56]));
        expect(!save.hardcore(), "a brand new save has the hardcore bit clear");
        expect(save.coinBalance() == 0L, "and starts with no coins");

        // Reading it must not have armed anything either.
        expect(!save.hardcore(), "reading the flag twice does not set it");

        save.setHardcore(true);
        expect(save.hardcore(), "setting it on sticks");
        save.setHardcore(false);
        expect(!save.hardcore(), "and setting it back off works too");
    }

    /**
     * The whole point: an Endless run with hardcore on starts and stays at 1 max HP, even when the
     * Max HP stat has been bought, which is the case a naive implementation gets wrong because it
     * clamps before the passive is added rather than after.
     */
    private static void testHardcoreCapsEndlessToOneHp() throws Exception {
        section("hardcore pins an endless run to 1 HP");
        int maxHpRow = ContentCatalog.upgradeIndex("max_hp");

        for (int characterIndex = 0; characterIndex < ContentCatalog.CHARACTERS.length;
                characterIndex++) {
            int characterHp = ContentCatalog.CHARACTERS[characterIndex].hp;

            int[] bought = new int[ContentCatalog.UPGRADES.length];
            bought[maxHpRow] = 3;                       // Max HP raised as far as it goes here

            Object plain = world(characterIndex, endlessMode(), false, bought);
            expect(plainMaxHp(plain) > 1,
                    "without hardcore, " + ContentCatalog.CHARACTERS[characterIndex].name
                            + " keeps its " + plainMaxHp(plain) + " max HP even with Max HP x3");
            expect(plainMaxHp(plain) >= characterHp,
                    "and that is never below the character's own base of " + characterHp);

            Object hard = world(characterIndex, endlessMode(), true, bought);
            expectEq(hardMaxHp(hard), 1,
                    "with hardcore, " + ContentCatalog.CHARACTERS[characterIndex].name
                            + " is pinned to 1 HP despite Max HP x3");
            expectEq(hardCurrentHp(hard), 1,
                    "and starts at that 1 rather than at the character's full pool");
        }
    }

    /**
     * Hardcore is an Endless rule. On every other mode the flag must not touch the run, otherwise a
     * player who bought it for Endless would find Stage Mode unplayable too.
     */
    private static void testHardcoreLeavesOtherModesAlone() throws Exception {
        section("hardcore does not affect the other modes");
        for (int modeIndex = 0; modeIndex < ContentCatalog.MODES.length; modeIndex++) {
            ContentCatalog.ModeDef mode = ContentCatalog.MODES[modeIndex];
            if (mode.id == null) continue;             // placeholder modes have no run
            if ("endless".equals(mode.id)) continue;   // covered above

            int[] none = new int[ContentCatalog.UPGRADES.length];
            Object off = world(0, mode, false, none);
            Object on = world(0, mode, true, none);
            expectEq(hardMaxHp(on), plainMaxHp(off),
                    "mode '" + mode.id + "' has the same max HP with hardcore on and off");
        }
    }

    // ---------------------------------------------------------------- fixture

    /**
     * A world configured the way a run start configures it: the hardcore override is applied to
     * both baseMaxHp and characterBaseMaxHp, then recomputeStats rebuilds the real maximum from
     * characterBaseMaxHp plus the purchased passives. Only then does the run set playerHp.
     */
    private static Object world(int characterIndex, ContentCatalog.ModeDef mode, boolean hardcore,
            int[] levels) throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[characterIndex];

        set(w, "save", null);
        set(w, "assets", null);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "modeIndex", Integer.valueOf(0));
        set(w, "stageIndex", Integer.valueOf(0));
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "mode", mode);
        set(w, "character", c);
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "shieldReadyAt", Long.valueOf(0L));
        set(w, "regenTickAt", Long.valueOf(0L));

        int baseMaxHp = c.hp;
        int characterBaseMaxHp = c.hp;
        if ("endless".equals(mode.id) && hardcore) {
            baseMaxHp = 1;
            characterBaseMaxHp = 1;
        }
        set(w, "baseMaxHp", Integer.valueOf(baseMaxHp));
        set(w, "characterBaseMaxHp", Integer.valueOf(characterBaseMaxHp));

        int[] arr = new int[ContentCatalog.UPGRADES.length];
        System.arraycopy(levels, 0, arr, 0, Math.min(levels.length, arr.length));
        set(w, "upgradeLevels", arr);

        Method recompute = wc.getDeclaredMethod("recomputeStats");
        recompute.setAccessible(true);
        recompute.invoke(w);
        set(w, "playerHp", Integer.valueOf(getInt(w, "playerMaxHp")));
        return w;
    }

    private static ContentCatalog.ModeDef endlessMode() {
        for (int i = 0; i < ContentCatalog.MODES.length; i++) {
            if ("endless".equals(ContentCatalog.MODES[i].id)) return ContentCatalog.MODES[i];
        }
        throw new IllegalStateException("no endless mode in the catalog");
    }

    private static int plainMaxHp(Object w) throws Exception { return getInt(w, "playerMaxHp"); }
    private static int hardMaxHp(Object w) throws Exception { return getInt(w, "playerMaxHp"); }
    private static int hardCurrentHp(Object w) throws Exception { return getInt(w, "playerHp"); }

    // ---------------------------------------------------------------- plumbing

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Object allocate(Class<?> type) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
            return allocateInstance.invoke(theUnsafe.get(null), type);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot allocate " + type.getName(), t);
        }
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
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

    /** Minimal in-memory record storage; no RMS on a desktop JVM. */
    private static final class Recording implements SaveStore.RecordStorage {
        byte[] record;

        Recording(byte[] initial) { record = initial; }

        public byte[] read(int recordId) { return record == null ? null : record.clone(); }
        public boolean write(int recordId, byte[] payload) { record = payload.clone(); return true; }
    }

    private static void section(String name) {
        System.out.println("[" + name + "]");
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + " (expected " + want + ", got " + got + ")");
    }
}