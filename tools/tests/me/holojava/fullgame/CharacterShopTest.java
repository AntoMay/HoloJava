package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the global shop: the passive catalog, the price curve, the packed save layout, the
 * one-time Skill Pasif purchase, the migration from the old per-character layout, the purchase
 * transaction, and the way a purchased passive actually reaches a run's stats.
 *
 * <p>Nothing here reimplements game logic. Prices come from {@code ContentCatalog.passiveCost},
 * storage from {@code SaveStore}, the effect from {@code RunWorld.recomputeStats}, and the
 * purchases from {@code FullGameCanvas.updateShop}, which routes rows to {@code buyPassive} and
 * {@code buySkillPassive}. Those are driven through reflection because {@code Canvas} and
 * {@code RunWorld} cannot be constructed on a desktop JVM and several of their fields are final.
 *
 * <p>The save layout matters most here: the shop is global, so the eight passives are eight
 * nibbles in bytes 10..13, the Skill Pasif flag is byte 14, and byte 46 carries the layout
 * version that makes the fold from the old per-character vector (bytes 10..29) detectable. A
 * careless accessor could silently spill one passive's purchase into its neighbour's nibble.
 */
public final class CharacterShopTest {

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
            } else {
                unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                        .invoke(unsafe, target, offset, value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
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

    private static void expectEq(long got, long want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static final class Recording implements SaveStore.RecordStorage {
        byte[] record;
        Recording(byte[] initial) { record = initial; }
        public byte[] read(int recordId) { return record == null ? null : record.clone(); }
        public boolean write(int recordId, byte[] payload) { record = payload.clone(); return true; }
    }

    // ---------------------------------------------------------------- fixtures

    private static Class<?> canvasClass() throws Exception {
        return Class.forName("me.holojava.fullgame.FullGameCanvas");
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Object canvas(SaveStore save, int characterIndex) throws Exception {
        Object c = allocate(canvasClass());
        set(c, "save", save);
        set(c, "assets", null);
        set(c, "characterIndex", Integer.valueOf(characterIndex));
        set(c, "screen", Integer.valueOf(10));      // SHOP
        set(c, "menuIndex", Integer.valueOf(0));
        set(c, "shopScroll", Integer.valueOf(0));
        return c;
    }

    /** Fires on the highlighted shop row through the real updateShop input path. */
    private static void shopFire(Object canvas, int row) throws Exception {
        set(canvas, "menuIndex", Integer.valueOf(row));
        Method m = canvasClass().getDeclaredMethod("updateShop", boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(canvas, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE);
    }

    private static int canvasInt(Object canvas, String field) throws Exception {
        return getInt(canvas, field);
    }

    /** A world with its pools and stat inputs present, plus the given passive levels applied. */
    private static Object world(SaveStore save, int characterIndex, int[] levels) throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        set(w, "save", save);
        set(w, "assets", null);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "modeIndex", Integer.valueOf(0));
        set(w, "stageIndex", Integer.valueOf(0));
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "character", ContentCatalog.CHARACTERS[characterIndex]);
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "baseMaxHp", Integer.valueOf(ContentCatalog.CHARACTERS[characterIndex].hp));
        set(w, "characterBaseMaxHp", Integer.valueOf(ContentCatalog.CHARACTERS[characterIndex].hp));
        set(w, "shieldReadyAt", Long.valueOf(0L));
        set(w, "regenTickAt", Long.valueOf(0L));
        int[] arr = new int[ContentCatalog.PASSIVES.length];
        System.arraycopy(levels, 0, arr, 0, levels.length);
        set(w, "passiveLevels", arr);
        return w;
    }

    private static void recompute(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("recomputeStats");
        m.setAccessible(true);
        m.invoke(w);
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testCatalogIsCoherent();
        testPriceCurveGrowsAndStops();
        testSaveStoresOneGlobalVector();
        testSaveRefusesImpossibleValues();
        testOldPerCharacterSavesFoldIntoTheGlobalVector();
        testMigrationPreservesEverythingElse();
        testPurchaseSpendsCoinsAndRaisesTheGlobalLevel();
        testPurchaseRefusedWhenTooPoorOrMaxed();
        testSkillPasifIsBoughtOnce();
        testShopRowsRouteToTheirActions();
        testPassivesReachRunStats();
        testDefenseAndCoinRateAreActuallyWired();
        testStartingCapitalConstant();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Every passive is well formed and maps to an effect the run understands. */
    private static void testCatalogIsCoherent() {
        System.out.println("[1] the passive catalog is coherent");
        expect(ContentCatalog.PASSIVES.length >= 5, "there are enough passives to be a choice");
        expectEq(ContentCatalog.PASSIVE_MAX_LEVEL, 5,
            "the shop sells 0-5 upgrades, as asked — not the old 0-10");
        boolean[] seenEffect = new boolean[ContentCatalog.EFFECT_COIN_PCT + 1];
        for (int i = 0; i < ContentCatalog.PASSIVES.length; i++) {
            ContentCatalog.PassiveDef d = ContentCatalog.passive(i);
            expect(d != null, "passive " + i + " resolves");
            expect(d.id != null && d.id.length() > 0, "passive " + i + " has an id");
            expect(d.name != null && d.name.length() > 0, "passive " + i + " has a display name");
            expectEq(ContentCatalog.passive(d.id) == d ? 0 : 1, 0,
                "passive '" + d.id + "' resolves by id back to itself");
            expect(d.maxLevel > 0, "passive '" + d.id + "' has a positive level cap");
            expect(d.baseCost > 0, "passive '" + d.id + "' costs something");
            expect(d.costGrowth >= 1, "passive '" + d.id + "' has a non-shrinking cost curve");
            expect(d.costCap >= d.baseCost,
                "passive '" + d.id + "' caps at or above its base cost");
            expect(d.effect >= 0 && d.effect <= ContentCatalog.EFFECT_COIN_PCT,
                "passive '" + d.id + "' uses a known effect id");
            expectEq(d.maxLevel, ContentCatalog.PASSIVE_MAX_LEVEL,
                "passive '" + d.id + "' runs the full 0-" + ContentCatalog.PASSIVE_MAX_LEVEL
                    + " range every other passive runs");
            seenEffect[d.effect] = true;
        }
        // The two effects the shop introduced must actually exist in the catalog.
        expect(seenEffect[ContentCatalog.EFFECT_DEFENSE_PCT], "a passive uses damage reduction");
        expect(seenEffect[ContentCatalog.EFFECT_COIN_PCT], "a passive uses coin rate");
    }

    /** Prices rise, never fall, stay positive, and stop offering past the cap. */
    private static void testPriceCurveGrowsAndStops() {
        System.out.println("[2] the price curve grows and stops at the cap");
        for (int i = 0; i < ContentCatalog.PASSIVES.length; i++) {
            ContentCatalog.PassiveDef d = ContentCatalog.passive(i);
            long previous = 0;
            for (int level = 0; level < d.maxLevel; level++) {
                long cost = ContentCatalog.passiveCost(d, level);
                expect(cost > 0, "'" + d.id + "' level " + (level + 1) + " costs something");
                expect(cost >= previous, "'" + d.id + "' price does not fall at level " + (level + 1));
                expect(cost <= d.costCap, "'" + d.id + "' price stays under the cap");
                previous = cost;
            }
            expectEq(ContentCatalog.passiveCost(d, d.maxLevel), -1L,
                "'" + d.id + "' is not purchasable past its cap");
            expectEq(ContentCatalog.passiveCost(d, -1), -1L,
                "'" + d.id + "' is not purchasable at a negative level");
        }
        expectEq(ContentCatalog.passiveCost(null, 0), -1L, "a null passive has no price");
    }

    /** The global vector round-trips across the whole 0..5 range and stamps the layout version. */
    private static void testSaveStoresOneGlobalVector() {
        System.out.println("[3] passive levels are stored as one global vector");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        int n = ContentCatalog.PASSIVES.length;
        // The modulus spans the full range the shop sells, so this pins 0 and the cap itself, not
        // just small values that any packing scheme would survive.
        int span = ContentCatalog.PASSIVE_MAX_LEVEL + 1;
        for (int p = 0; p < n; p++) {
            save.setPassiveLevel(p, p % span);
        }
        for (int p = 0; p < n; p++) {
            expectEq(save.passiveLevel(p), p % span, "passive " + p + " read back");
        }
        // Reload from the stored bytes: the packing must survive persistence, not just memory.
        SaveStore reloaded = new SaveStore(storage);
        for (int p = 0; p < n; p++) {
            expectEq(reloaded.passiveLevel(p), p % span, "passive " + p + " survived a reload");
        }
        expectEq(storage.record.length, 56,
            "the record kept its historical length, so no save has to migrate");
        expectEq(storage.record[46] & 0xff, 2, "the persisted record carries the layout version");
        expectEq(storage.record[14] & 0xff, 0, "and the Skill Pasif flag starts unowned");
    }

    /** Out-of-range input is refused rather than corrupting a neighbour's bits. */
    private static void testSaveRefusesImpossibleValues() {
        System.out.println("[4] impossible passive writes are refused");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.setPassiveLevel(0, 5);
        save.setPassiveLevel(ContentCatalog.PASSIVES.length, 3);   // no such passive
        save.setPassiveLevel(-1, 3);                               // no such passive either
        expectEq(save.passiveLevel(0), 5, "the one valid level survived");
        expectEq(save.passiveLevel(1), 0, "an unknown passive did not spill into its neighbour");
        expectEq(save.passiveLevel(2), 0, "a second unknown passive wrote nothing");
        expectEq(save.passiveLevel(ContentCatalog.PASSIVES.length), 0, "an unknown passive reads 0");
        expectEq(save.passiveLevel(-1), 0, "a negative passive reads 0");

        // The catalog cap is the ceiling: the cap itself stores, one past it does not, so the
        // storage layer enforces the same 0..5 range the shop sells.
        save.setPassiveLevel(0, ContentCatalog.PASSIVE_MAX_LEVEL);
        expectEq(save.passiveLevel(0), ContentCatalog.PASSIVE_MAX_LEVEL,
            "the cap itself is storable");
        save.setPassiveLevel(0, ContentCatalog.PASSIVE_MAX_LEVEL + 1);
        expectEq(save.passiveLevel(0), ContentCatalog.PASSIVE_MAX_LEVEL,
            "one past the cap is refused and the stored level is untouched");
    }

    /**
     * The migration: a record written by the per-character era (5 characters x 8 passives, 0..10,
     * bytes 10..29, no version stamp) folds into the global vector at one level per stat — the
     * highest any character had bought, clamped to the new cap — and the version is stamped so
     * later loads skip the fold.
     */
    private static void testOldPerCharacterSavesFoldIntoTheGlobalVector() {
        System.out.println("[5] an old per-character save folds into the global vector");
        int n = ContentCatalog.PASSIVES.length;
        byte[] old = new byte[56];
        for (int c = 0; c < ContentCatalog.CHARACTERS.length; c++) {
            for (int p = 0; p < n; p++) {
                int nibble = c * n + p;
                int index = 10 + nibble / 2;
                int shift = (nibble & 1) * 4;
                old[index] = (byte) ((old[index] & 0xff) | (((c + p) % 11) << shift));
            }
        }
        // Settings and coins must survive the fold untouched.
        old[47] = 0x01;                                     // hardcore
        for (int i = 0; i < 8; i++) old[48 + i] = (byte) (17 + i);

        SaveStore save = new SaveStore(new Recording(old));
        for (int p = 0; p < n; p++) {
            int best = 0;
            for (int c = 0; c < ContentCatalog.CHARACTERS.length; c++) {
                int v = (c + p) % 11;
                if (v > best) best = v;
            }
            if (best > ContentCatalog.PASSIVE_MAX_LEVEL) best = ContentCatalog.PASSIVE_MAX_LEVEL;
            expectEq(save.passiveLevel(p), best,
                "passive " + p + " folded to the best across characters, clamped");
        }
        expect(!save.skillPassiveOwned(), "the fold leaves Skill Pasif unowned");
        expect(save.hardcore(), "the settings byte survived the fold");
        // The coin bytes are big-endian, mirroring how SaveStore writes them: byte 48 is the most
        // significant, byte 55 the least.
        long coins = 0;
        for (int i = 0; i < 8; i++) coins = (coins << 8) | (old[48 + i] & 0xff);
        expectEq(save.coinBalance(), coins, "the coin balance survived the fold");

        // Reloading the same old record must fold to exactly the same result — the fold is
        // deterministic, so a save written by the old build always lands on the same vector.
        save.setSkillPassiveOwned(false);
        int[] first = new int[n];
        for (int p = 0; p < n; p++) first[p] = save.passiveLevel(p);
        SaveStore reloaded = new SaveStore(new Recording(old));
        for (int p = 0; p < n; p++) {
            expectEq(reloaded.passiveLevel(p), first[p],
                "a second load from the same old record folds to the same result");
        }
    }

    /** The fold may only touch the shop bytes: completion, settings and coins stay put. */
    private static void testMigrationPreservesEverythingElse() {
        System.out.println("[6] the migration touches nothing but the shop bytes");
        int n = ContentCatalog.PASSIVES.length;
        byte[] old = new byte[56];
        old[0] = (byte) 0x05;                               // character 0 cleared stages 0 and 2
        for (int c = 0; c < ContentCatalog.CHARACTERS.length; c++) {
            for (int p = 0; p < n; p++) {
                int nibble = c * n + p;
                int index = 10 + nibble / 2;
                int shift = (nibble & 1) * 4;
                old[index] = (byte) ((old[index] & 0xff) | (7 << shift));
            }
        }
        SaveStore save = new SaveStore(new Recording(old));
        expect(save.isCompleted(0, 0) && save.isCompleted(0, 2), "completion bits survived");
        expect(!save.isCompleted(0, 1) && !save.isCompleted(1, 0), "and unrelated ones stayed clear");
        for (int i = 15; i <= 29; i++) {
            expectEq(old[i] == 0 ? 0 : 1, 1, "the input record is never mutated (byte " + i + ")");
        }
        for (int p = 0; p < n; p++) {
            expectEq(save.passiveLevel(p), ContentCatalog.PASSIVE_MAX_LEVEL,
                "a 7 in the old 0..10 range clamps to the new cap (passive " + p + ")");
        }
    }

    /** A purchase moves exactly the price, raises exactly one global level, and stacks. */
    private static void testPurchaseSpendsCoinsAndRaisesTheGlobalLevel() throws Exception {
        System.out.println("[7] a purchase spends coins and raises the global level");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.creditCoins(1000000L);
        long before = save.coinBalance();
        int passive = 0;
        ContentCatalog.PassiveDef def = ContentCatalog.passive(passive);
        long cost = ContentCatalog.passiveCost(def, 0);

        Object c = canvas(save, 0);
        shopFire(c, passive);

        expectEq(save.passiveLevel(passive), 1, "the level went up");
        expectEq(save.coinBalance(), before - cost, "exactly the price was spent");
        expect(save.coinBalance() >= 0, "the balance did not go negative");

        // A second purchase of the same passive costs more, because the curve grows.
        long secondCost = ContentCatalog.passiveCost(def, 1);
        shopFire(c, passive);
        expectEq(save.passiveLevel(passive), 2, "a second purchase stacks");
        expectEq(save.coinBalance(), before - cost - secondCost, "and charged the grown price");
        expect(secondCost > cost, "the second price is higher than the first");
    }

    /** Too few coins and maxed rows both refuse, leaving the balance and level untouched. */
    private static void testPurchaseRefusedWhenTooPoorOrMaxed() throws Exception {
        System.out.println("[8] a purchase is refused when it cannot be paid or is maxed");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = canvas(save, 0);
        ContentCatalog.PassiveDef def = ContentCatalog.passive(0);

        // Nothing bought and no coins at all.
        shopFire(c, 0);
        expectEq(save.passiveLevel(0), 0, "nothing was bought with an empty balance");
        expectEq(save.coinBalance(), 0L, "the balance stayed at zero");

        // Just under the price.
        long cost = ContentCatalog.passiveCost(def, 0);
        save.creditCoins(cost - 1);
        long before = save.coinBalance();
        shopFire(c, 0);
        expectEq(save.passiveLevel(0), 0, "a short balance bought nothing");
        expectEq(save.coinBalance(), before, "and left the balance untouched");

        // Exactly the price is enough.
        save.creditCoins(1);
        shopFire(c, 0);
        expectEq(save.passiveLevel(0), 1, "the exact price is sufficient");

        // Max the row out, then try once more.
        long plenty = 100000000L;
        save.creditCoins(plenty);
        for (int level = 1; level < def.maxLevel; level++) shopFire(c, 0);
        expectEq(save.passiveLevel(0), def.maxLevel, "the row reached its cap");
        long afterMax = save.coinBalance();
        shopFire(c, 0);
        expectEq(save.passiveLevel(0), def.maxLevel, "a maxed row refuses another level");
        expectEq(save.coinBalance(), afterMax, "and charges nothing for the refusal");
    }

    /** Skill Pasif is a one-time 0/1 purchase: bought once, then refused forever. */
    private static void testSkillPasifIsBoughtOnce() throws Exception {
        System.out.println("[9] Skill Pasif is bought exactly once");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = canvas(save, 0);
        int row = ContentCatalog.PASSIVES.length;    // the Skill Pasif row

        expect(!save.skillPassiveOwned(), "a fresh save has not bought it");
        shopFire(c, row);
        expect(!save.skillPassiveOwned(), "an empty balance buys nothing");
        expectEq(save.coinBalance(), 0L, "and charges nothing");

        save.creditCoins(ContentCatalog.SKILL_PASSIVE_COST);
        long before = save.coinBalance();
        shopFire(c, row);
        expect(save.skillPassiveOwned(), "the exact price buys the skill");
        expectEq(save.coinBalance(), before - ContentCatalog.SKILL_PASSIVE_COST,
            "and only the fixed price was spent");

        // Buying again changes nothing: not the balance, not the flag.
        save.creditCoins(ContentCatalog.SKILL_PASSIVE_COST);
        long rich = save.coinBalance();
        shopFire(c, row);
        expect(save.skillPassiveOwned(), "it stays owned");
        expectEq(save.coinBalance(), rich, "a repeat purchase charges nothing");

        // The purchase survives persistence, like every other permanent upgrade.
        SaveStore reloaded = new SaveStore(storage);
        expect(reloaded.skillPassiveOwned(), "and survives a reload");
    }

    /** Row routing: passives buy, the Skill Pasif row buys it, Back and Left return to the menu. */
    private static void testShopRowsRouteToTheirActions() throws Exception {
        System.out.println("[10] shop rows route to their actions");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.creditCoins(1000000L);
        Object c = canvas(save, 0);

        // Every passive row buys its own passive and nothing else.
        for (int p = 0; p < ContentCatalog.PASSIVES.length; p++) {
            shopFire(c, p);
        }
        for (int p = 0; p < ContentCatalog.PASSIVES.length; p++) {
            expectEq(save.passiveLevel(p), 1, "row " + p + " bought passive " + p + " exactly once");
        }
        expectEq(save.skillPassiveOwned() ? 1 : 0, 0,
            "no passive row accidentally bought Skill Pasif");

        // The Back row returns to the main menu at the Shop entry.
        Object back = canvas(save, 0);
        shopFire(back, ContentCatalog.PASSIVES.length + 1);
        expectEq(canvasInt(back, "screen"), 0, "Back returns to the main menu");
        expectEq(canvasInt(back, "menuIndex"), 4, "with the cursor on the Shop entry");

        // Left does the same, from anywhere in the list.
        Object lefted = canvas(save, 0);
        set(lefted, "menuIndex", Integer.valueOf(2));
        Method m = canvasClass().getDeclaredMethod("updateShop", boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(lefted, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
        expectEq(canvasInt(lefted, "screen"), 0, "Left returns to the main menu");
        expectEq(canvasInt(lefted, "menuIndex"), 4, "with the cursor on the Shop entry");
    }

    /** Purchased levels move real run fields, through the production recompute path. */
    private static void testPassivesReachRunStats() throws Exception {
        System.out.println("[11] purchased passives reach the run's stats");
        int n = ContentCatalog.PASSIVES.length;
        int indexOf = -1;
        for (int i = 0; i < n; i++) {
            if (ContentCatalog.passive(i).effect == ContentCatalog.EFFECT_DAMAGE_PCT) indexOf = i;
        }
        expect(indexOf >= 0, "a damage passive exists");

        ContentCatalog.PassiveDef atk = ContentCatalog.passive(indexOf);
        Object bare = world(null, 0, new int[n]);
        recompute(bare);
        expectEq(getInt(bare, "atkPercentBonus"), 0, "no purchase means no attack bonus");

        int[] levels = new int[n];
        levels[indexOf] = 3;
        Object three = world(null, 0, levels);
        recompute(three);
        expectEq(getInt(three, "atkPercentBonus"), atk.value * 3,
            "three levels give three times the per-level attack bonus");

        // Max HP must scale with the character's own base, not stack on the run's current value.
        int hpIndex = -1;
        for (int i = 0; i < n; i++) {
            if (ContentCatalog.passive(i).effect == ContentCatalog.EFFECT_MAX_HP) hpIndex = i;
        }
        ContentCatalog.PassiveDef hp = ContentCatalog.passive(hpIndex);
        int[] hpLevels = new int[n];
        hpLevels[hpIndex] = 2;
        Object hpWorld = world(null, 0, hpLevels);
        recompute(hpWorld);
        int expected = ContentCatalog.CHARACTERS[0].hp
            + ContentCatalog.CHARACTERS[0].hp * hp.value * 2 / 100;
        expectEq(getInt(hpWorld, "playerMaxHp"), expected,
            "max HP gained the passive's share of the character's base HP");

        // The vector is global now, so another character sees the same purchase.
        Object other = world(null, 3, hpLevels);
        recompute(other);
        int otherExpected = ContentCatalog.CHARACTERS[3].hp
            + ContentCatalog.CHARACTERS[3].hp * hp.value * 2 / 100;
        expectEq(getInt(other, "playerMaxHp"), otherExpected,
            "a different character gains the same global purchase off its own base HP");

        // recomputeStats runs again on every item pickup; it must not compound.
        recompute(hpWorld);
        recompute(hpWorld);
        expectEq(getInt(hpWorld, "playerMaxHp"), expected,
            "recomputing twice does not compound the same purchase");
    }

    /** The two effects the shop introduced are the ones that were dead or missing before. */
    private static void testDefenseAndCoinRateAreActuallyWired() throws Exception {
        System.out.println("[12] damage reduction and coin rate are wired");
        int n = ContentCatalog.PASSIVES.length;
        int defIndex = -1, coinIndex = -1, spdIndex = -1;
        for (int i = 0; i < n; i++) {
            int e = ContentCatalog.passive(i).effect;
            if (e == ContentCatalog.EFFECT_DEFENSE_PCT && defIndex < 0) defIndex = i;
            if (e == ContentCatalog.EFFECT_COIN_PCT && coinIndex < 0) coinIndex = i;
            if (e == ContentCatalog.EFFECT_SPEED_PCT && spdIndex < 0) spdIndex = i;
        }
        expect(defIndex >= 0 && coinIndex >= 0 && spdIndex >= 0,
            "the shop covers defense, coin rate and speed");

        int[] levels = new int[n];
        levels[defIndex] = 4;
        levels[coinIndex] = 4;
        levels[spdIndex] = 4;
        Object w = world(null, 0, levels);
        recompute(w);

        ContentCatalog.PassiveDef def = ContentCatalog.passive(defIndex);
        ContentCatalog.PassiveDef coin = ContentCatalog.passive(coinIndex);
        ContentCatalog.PassiveDef spd = ContentCatalog.passive(spdIndex);
        expectEq(getInt(w, "defensePercent"), def.value * 4,
            "damage reduction is populated, which nothing in the run used to do");
        expectEq(getInt(w, "coinDropBonus"), coin.value * 4,
            "coin rate is populated");
        expect(getInt(w, "coinDropBonus") <= 90,
            "coin rate is capped short of a guaranteed drop");
        expectEq(getInt(w, "speedPercentBonus"), spd.value * 4, "speed is populated");

        // The coin roll itself must stay reachable: damagePlayer reads defensePercent, so verify
        // that path is now able to reduce rather than being dead weight.
        Method damage = worldClass().getDeclaredMethod("damagePlayer", int.class, long.class);
        damage.setAccessible(true);
        set(w, "state", Integer.valueOf(0));            // STATE_RUNNING
        set(w, "playerHp", Integer.valueOf(200));
        set(w, "invulUntil", Long.valueOf(0L));
        damage.invoke(w, Integer.valueOf(100), Long.valueOf(System.currentTimeMillis()));
        int reduced = 200 - getInt(w, "playerHp");
        expect(reduced < 100, "a 4-level Guard passive made a 100 point hit land for less ("
            + reduced + ")");
    }

    /** The temporary starting capital exists, is positive, and fits the balance model. */
    private static void testStartingCapitalConstant() throws Exception {
        System.out.println("[13] the starting capital is usable by the balance model");
        long capital = FullGameCanvas.SHOP_STARTING_CAPITAL;
        expect(capital > 0, "the starting capital is positive");
        expect(capital == 9999999L, "and is the amount that was asked for");
        expect(CoinBalance.canAdd(0L, capital), "the balance model accepts crediting it");
        // Enough to buy everything at least once, which is the point of the temporary grant.
        long total = 0;
        for (int i = 0; i < ContentCatalog.PASSIVES.length; i++) {
            total += ContentCatalog.passiveCost(ContentCatalog.passive(i), 0);
        }
        total += ContentCatalog.SKILL_PASSIVE_COST;
        expect(capital >= total,
            "the capital covers buying every passive once plus Skill Pasif (" + capital
                + " vs " + total + ")");
    }
}
