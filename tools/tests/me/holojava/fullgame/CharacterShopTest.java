package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the three-tab shop: the 34-row upgrade catalog, the wiki price tables, the packed save
 * layout (34 nibbles at bytes 10..26, layout version 3), the migration from both older layouts,
 * the purchase transaction, the locked rows, the Hardcore toggle, the Refund All flow, and the
 * way purchased levels actually reach a run's stats.
 *
 * <p>Nothing here reimplements game logic. Prices come from {@code ContentCatalog.upgradeCost},
 * storage from {@code SaveStore}, the effect from {@code RunWorld.recomputeStats}, and the
 * purchases from {@code FullGameCanvas.updateUpgrades}, which routes rows to
 * {@code buyUpgrade}. Those are driven through reflection because {@code Canvas} and
 * {@code RunWorld} cannot be constructed on a desktop JVM and several of their fields are final.
 *
 * <p>The save layout matters most here: the shop is global, so all 34 rows are 34 nibbles in
 * bytes 10..26 and byte 46 carries the layout version that makes both older layouts — the
 * eight-passive global vector (v2) and the per-character vector before it (v1) — detectable. A
 * careless accessor could silently spill one row's purchase into its neighbour's nibble.
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

    /** A canvas parked on the Beli Upgrade page (screen 11), cursor at the top of Ability. */
    private static Object upgradeCanvas(SaveStore save) throws Exception {
        Object c = allocate(canvasClass());
        set(c, "save", save);
        set(c, "assets", null);
        set(c, "characterIndex", Integer.valueOf(0));
        set(c, "screen", Integer.valueOf(11));      // UPGRADES
        set(c, "menuIndex", Integer.valueOf(0));
        set(c, "shopScroll", Integer.valueOf(0));
        set(c, "shopTab", Integer.valueOf(0));
        set(c, "refundConfirm", Boolean.FALSE);
        return c;
    }

    /**
     * Fires on one catalog row through the real updateUpgrades input path. Deliberately does not
     * touch refundConfirm: a fire press never cancels a pending confirmation in production, only
     * a cursor or tab move does — so the two-press Refund All flow must survive between calls.
     */
    private static void upgradeFire(Object canvas, int upgradeIndex) throws Exception {
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(upgradeIndex);
        set(canvas, "shopTab", Integer.valueOf(def.group));
        set(canvas, "menuIndex",
                Integer.valueOf(upgradeIndex - ContentCatalog.upgradeGroupStart(def.group)));
        Method m = canvasClass().getDeclaredMethod("updateUpgrades", boolean.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(canvas, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE);
    }

    private static void upgradesMove(Object canvas, boolean up, boolean down) throws Exception {
        Method m = canvasClass().getDeclaredMethod("updateUpgrades", boolean.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(canvas, Boolean.valueOf(up), Boolean.valueOf(down), Boolean.FALSE,
                Boolean.FALSE, Boolean.FALSE);
    }

    /** A world with its pools and stat inputs present, plus the given upgrade levels applied. */
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
        int[] arr = new int[ContentCatalog.UPGRADES.length];
        System.arraycopy(levels, 0, arr, 0, Math.min(levels.length, arr.length));
        set(w, "upgradeLevels", arr);
        return w;
    }

    private static void recompute(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("recomputeStats");
        m.setAccessible(true);
        m.invoke(w);
    }

    /** The rows the legacy eight-passive vectors map onto, in the old order. */
    private static final String[] LEGACY_TARGETS = {
        "max_hp", "atk_up", "defense_up", "spd_up",
        "crit_up", "exp_up", "money_up", "haste_up"
    };

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testCatalogIsCoherent();
        testWikiPricesMatchTheTables();
        testSaveStoresOneGlobalVector();
        testSaveRefusesImpossibleValues();
        testV2SavesRemapIntoTheNewRows();
        testV1PerCharacterSavesFoldAndRemap();
        testMigrationPreservesEverythingElse();
        testPurchaseSpendsCoinsAndRaisesTheGlobalLevel();
        testPurchaseRefusedWhenTooPoorOrMaxed();
        testLockedRowsNeverBuy();
        testHardcoreTogglesAndRefundNeedsTwoPresses();
        testRefundAllCreditsEveryOwnedLevel();
        testUpgradeRowsReachRunStats();
        testStartingCapitalConstant();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Every row is well formed, grouped contiguously, and maps to an effect the run knows. */
    private static void testCatalogIsCoherent() {
        System.out.println("[1] the upgrade catalog is coherent");
        expectEq(ContentCatalog.UPGRADES.length, 34, "the shop has the 34 rows of the wiki tables");
        expectEq(ContentCatalog.upgradeGroupCount(ContentCatalog.GROUP_ABILITY), 12,
            "the Ability tab holds 12 rows");
        expectEq(ContentCatalog.upgradeGroupCount(ContentCatalog.GROUP_STAT), 14,
            "the Stat tab holds 14 rows");
        expectEq(ContentCatalog.upgradeGroupCount(ContentCatalog.GROUP_OTHERS), 8,
            "the Lainnya tab holds 8 rows");
        expectEq(ContentCatalog.upgradeGroupStart(ContentCatalog.GROUP_ABILITY), 0,
            "Ability starts at index 0");
        expectEq(ContentCatalog.upgradeGroupStart(ContentCatalog.GROUP_STAT), 12,
            "Stat starts at index 12");
        expectEq(ContentCatalog.upgradeGroupStart(ContentCatalog.GROUP_OTHERS), 26,
            "Lainnya starts at index 26");

        boolean[] seenEffect = new boolean[ContentCatalog.EFFECT_COIN_PCT + 1];
        int lockedCount = 0;
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef d = ContentCatalog.upgrade(i);
            expect(d != null, "row " + i + " resolves");
            expect(d.id != null && d.id.length() > 0, "row " + i + " has an id");
            expect(d.name != null && d.name.length() > 0, "row " + i + " has a display name");
            expect(d.desc != null && d.desc.length() > 0,
                "row '" + d.id + "' has an effect line for the detail panel");
            expectEq(ContentCatalog.upgrade(d.id) == d ? 0 : 1, 0,
                "row '" + d.id + "' resolves by id back to itself");
            expectEq(ContentCatalog.upgradeIndex(d.id), i,
                "row '" + d.id + "' resolves by id back to its index");
            expectEq(d.maxLevel, d.prices.length,
                "row '" + d.id + "' never advertises more levels than it has prices for");
            expect(d.maxLevel > 0, "row '" + d.id + "' sells at least one level");
            expect(d.group >= ContentCatalog.GROUP_ABILITY && d.group <= ContentCatalog.GROUP_OTHERS,
                "row '" + d.id + "' belongs to a known tab");
            expect(d.buyKind >= ContentCatalog.BUY_LEVEL && d.buyKind <= ContentCatalog.BUY_ACTION,
                "row '" + d.id + "' uses a known buy kind");
            if (d.effect == -1) {
                expect(d.value == 0, "row '" + d.id + "' carries no value without an effect");
            } else {
                expect(d.effect >= 0 && d.effect <= ContentCatalog.EFFECT_COIN_PCT,
                    "row '" + d.id + "' uses a known effect id");
                expect(d.value > 0, "row '" + d.id + "' has a per-level value");
                seenEffect[d.effect] = true;
            }
            long previous = -1;
            for (int l = 0; l < d.prices.length; l++) {
                expect(d.prices[l] >= 0, "row '" + d.id + "' price " + (l + 1) + " is not negative");
                expect(d.prices[l] >= previous,
                    "row '" + d.id + "' price does not fall at level " + (l + 1));
                previous = d.prices[l];
            }
            if (d.locked) {
                lockedCount++;
                expectEq(ContentCatalog.upgradeCost(d, 0), -1L,
                    "row '" + d.id + "' is locked, so it has no purchasable price");
            }
        }
        expectEq(lockedCount, 15,
            "the 15 rows without gameplay yet are marked locked");
        // The effects the shop must actually move the run's stats with:
        expect(seenEffect[ContentCatalog.EFFECT_MAX_HP], "a row raises max HP");
        expect(seenEffect[ContentCatalog.EFFECT_DAMAGE_PCT], "a row raises damage");
        expect(seenEffect[ContentCatalog.EFFECT_DEFENSE_PCT], "a row raises defense");
        expect(seenEffect[ContentCatalog.EFFECT_COIN_PCT], "a row raises coin value");
        expect(seenEffect[ContentCatalog.EFFECT_PICKUP_PCT], "a row raises pickup range");
        expect(seenEffect[ContentCatalog.EFFECT_REGEN], "a row raises regeneration");
    }

    /** The price tables are the wiki tables: spot checks on every shape they come in. */
    private static void testWikiPricesMatchTheTables() {
        System.out.println("[2] the price tables match the wiki");
        ContentCatalog.UpgradeDef special = ContentCatalog.upgrade("special_attack");
        expectEq(special.prices.length, 1, "Special Attack is bought once");
        expectEq(special.prices[0], 500L, "Special Attack costs 500");

        ContentCatalog.UpgradeDef growth = ContentCatalog.upgrade("growth");
        expectEq(growth.prices.length, 3, "Growth runs 3 levels");
        expectEq(growth.prices[0], 500L, "Growth level 1 costs 500");
        expectEq(growth.prices[1], 10000L, "Growth level 2 costs 10,000");
        expectEq(growth.prices[2], 50000L, "Growth level 3 costs 50,000");

        ContentCatalog.UpgradeDef reroll = ContentCatalog.upgrade("reroll");
        expectEq(reroll.prices.length, 10, "Reroll runs 10 levels");
        expectEq(reroll.prices[0], 2500L, "Reroll starts at 2,500");
        expectEq(reroll.prices[9], 25000L, "Reroll ends at 25,000");

        ContentCatalog.UpgradeDef hold = ContentCatalog.upgrade("hold");
        expectEq(hold.prices.length, 5, "Tahan runs 5 levels");
        expectEq(hold.prices[0], 5000L, "Tahan starts at 5,000");
        expectEq(hold.prices[4], 25000L, "Tahan ends at 25,000");

        ContentCatalog.UpgradeDef hp = ContentCatalog.upgrade("max_hp");
        expectEq(hp.prices.length, 10, "Max HP Up runs 10 levels");
        expectEq(hp.prices[0], 200L, "Max HP Up starts at 200");
        expectEq(hp.prices[9], 36000L, "Max HP Up ends at 36,000");

        ContentCatalog.UpgradeDef atk = ContentCatalog.upgrade("atk_up");
        expectEq(atk.prices[9], 43000L, "ATK Up ends at 43,000");

        ContentCatalog.UpgradeDef money = ContentCatalog.upgrade("money_up");
        expectEq(money.prices[0], 400L, "Money Gain Up starts at 400");
        expectEq(money.prices[9], 60000L, "Money Gain Up ends at 60,000");

        ContentCatalog.UpgradeDef skillUp = ContentCatalog.upgrade("skill_up");
        expectEq(skillUp.prices[9], 60000L, "Skill Up ends at 60,000");

        ContentCatalog.UpgradeDef weaponLimit = ContentCatalog.upgrade("weapon_limit");
        expectEq(weaponLimit.prices.length, 5, "Weapon Limit runs 5 levels");
        for (int l = 0; l < weaponLimit.prices.length; l++) {
            expectEq(weaponLimit.prices[l], 0L, "Weapon Limit level " + (l + 1) + " is free");
        }
        ContentCatalog.UpgradeDef itemLimit = ContentCatalog.upgrade("item_limit");
        expectEq(itemLimit.prices.length, 6, "Item Limit runs 6 levels");
        ContentCatalog.UpgradeDef collabBan = ContentCatalog.upgrade("collab_ban");
        expectEq(collabBan.prices[0], 0L, "Collab Ban is free");

        // The two non-levelled rows are exactly the two the wiki does not price per level.
        expectEq(ContentCatalog.upgrade("hardcore").buyKind, ContentCatalog.BUY_TOGGLE,
            "Hardcore is a toggle, mirroring the settings bit");
        expectEq(ContentCatalog.upgrade("refund_all").buyKind, ContentCatalog.BUY_ACTION,
            "Refund All is an action, not a levelled purchase");
    }

    /** The global vector round-trips across every buyable row and stamps layout version 3. */
    private static void testSaveStoresOneGlobalVector() {
        System.out.println("[3] upgrade levels are stored as one global vector");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef d = ContentCatalog.upgrade(i);
            if (d.locked || d.buyKind != ContentCatalog.BUY_LEVEL) continue;
            save.setUpgradeLevel(i, i % (d.maxLevel + 1));
        }
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef d = ContentCatalog.upgrade(i);
            if (d.locked || d.buyKind != ContentCatalog.BUY_LEVEL) continue;
            expectEq(save.upgradeLevel(i), i % (d.maxLevel + 1), "row " + d.id + " read back");
        }
        // Reload from the stored bytes: the packing must survive persistence, not just memory.
        SaveStore reloaded = new SaveStore(storage);
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef d = ContentCatalog.upgrade(i);
            if (d.locked || d.buyKind != ContentCatalog.BUY_LEVEL) continue;
            expectEq(reloaded.upgradeLevel(i), i % (d.maxLevel + 1),
                "row " + d.id + " survived a reload");
        }
        expectEq(storage.record.length, 56,
            "the record kept its historical length, so no save has to migrate");
        expectEq(storage.record[46] & 0xff, 3, "the persisted record carries layout version 3");
    }

    /** Out-of-range input is refused rather than corrupting a neighbour's bits. */
    private static void testSaveRefusesImpossibleValues() {
        System.out.println("[4] impossible upgrade writes are refused");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        int reroll = ContentCatalog.upgradeIndex("reroll");
        ContentCatalog.UpgradeDef rerollDef = ContentCatalog.upgrade(reroll);

        save.setUpgradeLevel(reroll, 3);
        save.setUpgradeLevel(ContentCatalog.UPGRADES.length, 3);   // no such row
        save.setUpgradeLevel(-1, 3);                               // no such row either
        expectEq(save.upgradeLevel(reroll), 3, "the one valid level survived");
        expectEq(save.upgradeLevel(ContentCatalog.UPGRADES.length), 0, "an unknown row reads 0");
        expectEq(save.upgradeLevel(-1), 0, "a negative row reads 0");
        expectEq(save.upgradeLevel(reroll + 1), 0,
            "an unknown row did not spill into its neighbour");

        // The row's own cap is the ceiling: the cap stores, one past it does not.
        save.setUpgradeLevel(reroll, rerollDef.maxLevel);
        expectEq(save.upgradeLevel(reroll), rerollDef.maxLevel, "the cap itself is storable");
        save.setUpgradeLevel(reroll, rerollDef.maxLevel + 1);
        expectEq(save.upgradeLevel(reroll), rerollDef.maxLevel,
            "one past the cap is refused and the stored level is untouched");

        // Rows that are not for sale or not levelled never write a nibble at all.
        int growth = ContentCatalog.upgradeIndex("growth");
        save.setUpgradeLevel(growth, 1);
        expectEq(save.upgradeLevel(growth), 0, "a locked row never stores a level");
        expectEq(save.upgradeLevel(growth - 1), 0,
            "and the locked write did not leak into the row before it");

        int hardcore = ContentCatalog.upgradeIndex("hardcore");
        save.setUpgradeLevel(hardcore, 1);
        expectEq(save.upgradeLevel(hardcore), 0,
            "Hardcore mirrors the settings bit and never stores a nibble");
        int refundAll = ContentCatalog.upgradeIndex("refund_all");
        save.setUpgradeLevel(refundAll, 1);
        expectEq(save.upgradeLevel(refundAll), 0, "Refund All stores nothing");
    }

    /** A layout-v2 record (eight global passive nibbles) remaps onto the new stat rows. */
    private static void testV2SavesRemapIntoTheNewRows() {
        System.out.println("[5] a layout-v2 save remaps onto the new rows");
        byte[] old = new byte[56];
        old[46] = 2;                                        // the v2 layout stamp
        old[14] = 1;                                        // the long-gone Skill Pasif flag
        for (int p = 0; p < 8; p++) {                       // one value per legacy passive
            int shift = (p & 1) * 4;
            old[10 + p / 2] = (byte) ((old[10 + p / 2] & 0xff) | ((p + 1) << shift));
        }
        old[47] = 0x01;                                     // hardcore, must survive
        for (int i = 0; i < 8; i++) old[48 + i] = (byte) (17 + i);   // coins, must survive

        SaveStore save = new SaveStore(new Recording(old));
        for (int p = 0; p < 8; p++) {
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(LEGACY_TARGETS[p]);
            int want = p + 1;
            if (want > def.maxLevel) want = def.maxLevel;
            expectEq(save.upgradeLevel(ContentCatalog.upgradeIndex(LEGACY_TARGETS[p])), want,
                "legacy passive " + p + " landed on " + def.id + " clamped to its new cap");
        }
        expectEq(save.upgradeLevel(8), 0,
            "the old Skill Pasif flag byte is now plain upgrade storage and was cleared");
        expectEq(save.upgradeLevel(ContentCatalog.upgradeIndex("special_attack")), 0,
            "and no unrelated row gained a level");
        expect(save.hardcore(), "the settings byte survived the remap");

        long coins = 0;
        for (int i = 0; i < 8; i++) coins = (coins << 8) | (old[48 + i] & 0xff);
        expectEq(save.coinBalance(), coins, "the coin balance survived the remap");

        // Reloading the same old record must remap to exactly the same result.
        SaveStore reloaded = new SaveStore(new Recording(old));
        for (int p = 0; p < 8; p++) {
            expectEq(reloaded.upgradeLevel(ContentCatalog.upgradeIndex(LEGACY_TARGETS[p])),
                save.upgradeLevel(ContentCatalog.upgradeIndex(LEGACY_TARGETS[p])),
                "a second load from the same v2 record remaps to the same level");
        }
    }

    /** A pre-v2 record (5 characters x 8 passives in bytes 10..29) folds, then remaps. */
    private static void testV1PerCharacterSavesFoldAndRemap() {
        System.out.println("[6] an old per-character save folds into the global vector");
        byte[] old = new byte[56];
        for (int c = 0; c < ContentCatalog.CHARACTERS.length; c++) {
            for (int p = 0; p < 8; p++) {
                int nibble = c * 8 + p;
                int index = 10 + nibble / 2;
                int shift = (nibble & 1) * 4;
                old[index] = (byte) ((old[index] & 0xff) | (((c + p) % 11) << shift));
            }
        }
        old[47] = 0x01;                                     // hardcore, must survive
        for (int i = 0; i < 8; i++) old[48 + i] = (byte) (21 + i);   // coins, must survive

        SaveStore save = new SaveStore(new Recording(old));
        for (int p = 0; p < 8; p++) {
            int best = 0;
            for (int c = 0; c < ContentCatalog.CHARACTERS.length; c++) {
                int v = (c + p) % 11;
                if (v > best) best = v;
            }
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(LEGACY_TARGETS[p]);
            if (best > def.maxLevel) best = def.maxLevel;
            expectEq(save.upgradeLevel(ContentCatalog.upgradeIndex(LEGACY_TARGETS[p])), best,
                "passive " + p + " folded to the best across characters, clamped, on " + def.id);
        }
        expect(save.hardcore(), "the settings byte survived the fold");
        long coins = 0;
        for (int i = 0; i < 8; i++) coins = (coins << 8) | (old[48 + i] & 0xff);
        expectEq(save.coinBalance(), coins, "the coin balance survived the fold");
        expectEq(save.upgradeLevel(ContentCatalog.upgradeIndex("reroll")), 0,
            "no unrelated row gained a level from the fold");
    }

    /** The migration may only touch the shop bytes: completion and the input stay put. */
    private static void testMigrationPreservesEverythingElse() {
        System.out.println("[7] the migration touches nothing but the shop bytes");
        byte[] old = new byte[56];
        old[0] = (byte) 0x05;                               // character 0 cleared stages 0 and 2
        old[46] = 2;                                        // v2: one pass, byte 10..13 in play
        for (int p = 0; p < 8; p++) {                       // fill the legacy vector with 7s
            int shift = (p & 1) * 4;
            old[10 + p / 2] = (byte) ((old[10 + p / 2] & 0xff) | (7 << shift));
        }
        byte[] input = (byte[]) old.clone();

        SaveStore save = new SaveStore(new Recording(old));
        expect(save.isCompleted(0, 0) && save.isCompleted(0, 2), "completion bits survived");
        expect(!save.isCompleted(0, 1) && !save.isCompleted(1, 0), "and unrelated ones stayed clear");
        for (int i = 0; i < 56; i++) {
            expectEq(old[i], input[i], "the input record is never mutated (byte " + i + ")");
        }
        for (int p = 0; p < 8; p++) {
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(LEGACY_TARGETS[p]);
            int want = 7 > def.maxLevel ? def.maxLevel : 7;
            expectEq(save.upgradeLevel(ContentCatalog.upgradeIndex(LEGACY_TARGETS[p])), want,
                "a 7 in the old range clamps to the new cap on " + def.id);
        }
    }

    /** A purchase moves exactly the price, raises exactly one global level, and stacks. */
    private static void testPurchaseSpendsCoinsAndRaisesTheGlobalLevel() throws Exception {
        System.out.println("[8] a purchase spends coins and raises the global level");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.creditCoins(1000000L);
        long before = save.coinBalance();
        int reroll = ContentCatalog.upgradeIndex("reroll");
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(reroll);
        long cost = ContentCatalog.upgradeCost(def, 0);

        Object c = upgradeCanvas(save);
        upgradeFire(c, reroll);

        expectEq(save.upgradeLevel(reroll), 1, "the level went up");
        expectEq(save.coinBalance(), before - cost, "exactly the price was spent");
        expect(save.coinBalance() >= 0, "the balance did not go negative");

        // A second purchase of the same row costs more, because the wiki table grows.
        long secondCost = ContentCatalog.upgradeCost(def, 1);
        upgradeFire(c, reroll);
        expectEq(save.upgradeLevel(reroll), 2, "a second purchase stacks");
        expectEq(save.coinBalance(), before - cost - secondCost, "and charged the grown price");
        expect(secondCost > cost, "the second price is higher than the first");

        // A free row (0 coins) still levelled, and never touched the balance.
        int collabBan = ContentCatalog.upgradeIndex("collab_ban");
        long free = save.coinBalance();
        upgradeFire(c, collabBan);
        expectEq(save.upgradeLevel(collabBan), 1, "the free Collab Ban row levelled anyway");
        expectEq(save.coinBalance(), free, "and charged nothing for it");
    }

    /** Too few coins and maxed rows both refuse, leaving the balance and level untouched. */
    private static void testPurchaseRefusedWhenTooPoorOrMaxed() throws Exception {
        System.out.println("[9] a purchase is refused when it cannot be paid or is maxed");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        Object c = upgradeCanvas(save);
        int reroll = ContentCatalog.upgradeIndex("reroll");
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(reroll);

        // Nothing bought and no coins at all.
        upgradeFire(c, reroll);
        expectEq(save.upgradeLevel(reroll), 0, "nothing was bought with an empty balance");
        expectEq(save.coinBalance(), 0L, "the balance stayed at zero");

        // Just under the price.
        long cost = ContentCatalog.upgradeCost(def, 0);
        save.creditCoins(cost - 1);
        long before = save.coinBalance();
        upgradeFire(c, reroll);
        expectEq(save.upgradeLevel(reroll), 0, "a short balance bought nothing");
        expectEq(save.coinBalance(), before, "and left the balance untouched");

        // Exactly the price is enough.
        save.creditCoins(1);
        upgradeFire(c, reroll);
        expectEq(save.upgradeLevel(reroll), 1, "the exact price is sufficient");

        // Max the row out, then try once more.
        save.creditCoins(100000000L);
        for (int level = 1; level < def.maxLevel; level++) upgradeFire(c, reroll);
        expectEq(save.upgradeLevel(reroll), def.maxLevel, "the row reached its cap");
        long afterMax = save.coinBalance();
        upgradeFire(c, reroll);
        expectEq(save.upgradeLevel(reroll), def.maxLevel, "a maxed row refuses another level");
        expectEq(save.coinBalance(), afterMax, "and charges nothing for the refusal");
    }

    /** A locked row is data for the page only: no level, no charge, ever. */
    private static void testLockedRowsNeverBuy() throws Exception {
        System.out.println("[10] locked rows never buy");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.creditCoins(10000000L);
        Object c = upgradeCanvas(save);

        int checked = 0;
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(i);
            if (!def.locked) continue;
            checked++;
            long before = save.coinBalance();
            upgradeFire(c, i);
            expectEq(save.upgradeLevel(i), 0, "locked row '" + def.id + "' stayed at level 0");
            expectEq(save.coinBalance(), before, "locked row '" + def.id + "' charged nothing");
        }
        expectEq(checked, 15, "every one of the 15 locked rows was exercised");
    }

    /** Hardcore toggles the shared settings flag; Refund All needs two deliberate presses. */
    private static void testHardcoreTogglesAndRefundNeedsTwoPresses() throws Exception {
        System.out.println("[11] Hardcore toggles, Refund All confirms");
        Recording storage = new Recording(new byte[56]);
        SaveStore save = new SaveStore(storage);
        save.creditCoins(1000000L);
        Object c = upgradeCanvas(save);

        int hardcore = ContentCatalog.upgradeIndex("hardcore");
        int refundAll = ContentCatalog.upgradeIndex("refund_all");
        expect(!save.hardcore(), "hardcore starts off");
        upgradeFire(c, hardcore);
        expect(save.hardcore(), "the Hardcore row toggles the settings flag itself");
        upgradeFire(c, hardcore);
        expect(!save.hardcore(), "and pressing again toggles it back off");

        // Buy two rows, then arm a refund and let a cursor move cancel it.
        int reroll = ContentCatalog.upgradeIndex("reroll");
        int hp = ContentCatalog.upgradeIndex("max_hp");
        upgradeFire(c, reroll);
        upgradeFire(c, hp);
        long worth = save.refundValue();
        long balance = save.coinBalance();
        expect(worth > 0, "the two purchases are worth something");

        upgradeFire(c, refundAll);
        expect(getBool(c, "refundConfirm"), "the first press only arms the confirmation");
        expectEq(save.upgradeLevel(reroll), 1, "nothing was refunded yet");
        upgradesMove(c, true, false);                     // any cursor move cancels
        expect(!getBool(c, "refundConfirm"), "a cursor move cancels the pending confirmation");

        upgradeFire(c, refundAll);                        // arm again...
        upgradeFire(c, refundAll);                        // ...and confirm
        expect(!getBool(c, "refundConfirm"), "the confirmation cleared");
        expectEq(save.upgradeLevel(reroll), 0, "the reroll level was refunded");
        expectEq(save.upgradeLevel(hp), 0, "the stat level was refunded");
        expectEq(save.coinBalance(), balance + worth, "and the full price came back");
    }

    /** Refund All as a save operation: exact arithmetic, one write, idempotent. */
    private static void testRefundAllCreditsEveryOwnedLevel() throws Exception {
        System.out.println("[12] Refund All credits exactly what was paid");
        SaveStore save = new SaveStore(new Recording(new byte[56]));
        save.creditCoins(50000L);
        int reroll = ContentCatalog.upgradeIndex("reroll");
        int hp = ContentCatalog.upgradeIndex("max_hp");
        save.setUpgradeLevel(reroll, 2);                  // 2500 + 5000
        save.setUpgradeLevel(hp, 3);                      // 200 + 500 + 1200
        long worth = save.refundValue();
        expectEq(worth, 2500L + 5000L + 200L + 500L + 1200L,
            "the refund value sums the wiki prices that were actually paid");
        long before = save.coinBalance();

        expect(save.refundAll(), "the refund landed");
        expectEq(save.coinBalance(), before + worth, "and credited the full price");
        expectEq(save.upgradeLevel(reroll), 0, "the reroll levels were zeroed");
        expectEq(save.upgradeLevel(hp), 0, "the stat levels were zeroed");

        expect(save.refundAll(), "an empty refund still succeeds as a no-op");
        expectEq(save.coinBalance(), before + worth, "and credits nothing the second time");
    }

    /** Purchased levels move real run fields, through the production recompute path. */
    private static void testUpgradeRowsReachRunStats() throws Exception {
        System.out.println("[13] purchased levels reach the run's stats");
        int n = ContentCatalog.UPGRADES.length;
        int atk = ContentCatalog.upgradeIndex("atk_up");
        int hp = ContentCatalog.upgradeIndex("max_hp");
        int def = ContentCatalog.upgradeIndex("defense_up");
        int money = ContentCatalog.upgradeIndex("money_up");
        int spd = ContentCatalog.upgradeIndex("spd_up");
        int pickup = ContentCatalog.upgradeIndex("pickup_range");
        int regen = ContentCatalog.upgradeIndex("regeneration");
        int lockedRow = ContentCatalog.upgradeIndex("special_cdr");

        Object bare = world(null, 0, new int[n]);
        recompute(bare);
        expectEq(getInt(bare, "atkPercentBonus"), 0, "no purchase means no attack bonus");
        expectEq(getInt(bare, "regenHealPerTick"), 0, "and no passive regeneration");

        int[] levels = new int[n];
        levels[atk] = 3;
        levels[def] = 4;
        levels[spd] = 4;
        levels[pickup] = 2;
        levels[regen] = 3;
        levels[lockedRow] = 5;                            // locked rows must change nothing
        Object w = world(null, 0, levels);
        recompute(w);
        expectEq(getInt(w, "atkPercentBonus"),
            ContentCatalog.upgrade(atk).value * 3, "three ATK levels give three times the value");
        expectEq(getInt(w, "defensePercent"),
            ContentCatalog.upgrade(def).value * 4, "defense is populated");
        expectEq(getInt(w, "speedPercentBonus"),
            ContentCatalog.upgrade(spd).value * 4, "speed is populated");
        expectEq(getInt(w, "pickupPercentBonus"),
            ContentCatalog.upgrade(pickup).value * 2, "pickup range is populated");
        expectEq(getInt(w, "regenHealPerTick"),
            ContentCatalog.upgrade(regen).value * 3, "regeneration heals per level per tick");

        // Max HP must scale with the character's own base, not stack on the run's current value.
        int[] hpLevels = new int[n];
        hpLevels[hp] = 2;
        Object hpWorld = world(null, 0, hpLevels);
        recompute(hpWorld);
        int expected = ContentCatalog.CHARACTERS[0].hp
            + ContentCatalog.CHARACTERS[0].hp * ContentCatalog.upgrade(hp).value * 2 / 100;
        expectEq(getInt(hpWorld, "playerMaxHp"), expected,
            "max HP gained the row's share of the character's base HP");

        // The vector is global now, so another character sees the same purchase.
        Object other = world(null, 3, hpLevels);
        recompute(other);
        int otherExpected = ContentCatalog.CHARACTERS[3].hp
            + ContentCatalog.CHARACTERS[3].hp * ContentCatalog.upgrade(hp).value * 2 / 100;
        expectEq(getInt(other, "playerMaxHp"), otherExpected,
            "a different character gains the same global purchase off its own base HP");

        // recomputeStats runs again on every pickup; it must not compound.
        recompute(hpWorld);
        recompute(hpWorld);
        expectEq(getInt(hpWorld, "playerMaxHp"), expected,
            "recomputing twice does not compound the same purchase");

        // Money Gain Up caps at the wiki's +200%, and an injected overrun still lands on the cap.
        int[] moneyLevels = new int[n];
        moneyLevels[money] = 15;
        Object moneyWorld = world(null, 0, moneyLevels);
        recompute(moneyWorld);
        expectEq(getInt(moneyWorld, "coinDropBonus"), 200,
            "coin value stops at the +200% cap");
    }

    /** The temporary starting capital exists, is positive, and fits the balance model. */
    private static void testStartingCapitalConstant() throws Exception {
        System.out.println("[14] the starting capital is usable by the balance model");
        long capital = FullGameCanvas.SHOP_STARTING_CAPITAL;
        expect(capital > 0, "the starting capital is positive");
        expect(capital == 9999999L, "and is the amount that was asked for");
        expect(CoinBalance.canAdd(0L, capital), "the balance model accepts crediting it");

        // Enough to buy the first level of every row at least once, which is the point of the
        // temporary grant: a tester can open every tab and buy across it.
        long total = 0;
        int priced = 0;
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef d = ContentCatalog.upgrade(i);
            if (d.prices == null || d.prices.length == 0) continue;
            total += d.prices[0];
            priced++;
        }
        expect(priced == 34, "every row has a first-level price (" + priced + ")");
        expect(capital >= total,
            "the capital covers buying every row's first level (" + capital + " vs " + total + ")");
    }
}
