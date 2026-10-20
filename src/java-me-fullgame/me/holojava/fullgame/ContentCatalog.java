package me.holojava.fullgame;

/** Data-only catalog. Engine behavior is selected through primitive pattern identifiers. */
final class ContentCatalog {
    // The wiki catalog is tracked separately in data/wiki/; only verified local
    // playable actors are exposed in the runtime menu.
    static final int DOCUMENTED_CHARACTER_COUNT = 47;
    static final int DOCUMENTED_WEAPON_COUNT = 94;
    static final int DOCUMENTED_ITEM_COUNT = 37;
    static final String WIKI_VERSION = "Update 0.7 / current wiki.gg tables";

    static final int WEAPON_MELEE = 0;
    static final int WEAPON_MULTISHOT = 1;
    static final int WEAPON_RANGED = 2;

    static final int PATTERN_MELEE_ARC = 0;
    static final int PATTERN_PROJECTILE = 1;
    static final int PATTERN_ORBIT = 2;
    static final int PATTERN_BOOMERANG = 3;
    static final int PATTERN_AOE = 4;
    static final int PATTERN_HOMING = 5;
    static final int PATTERN_PIERCING = 6;
    static final int PATTERN_WALL = 7;

    static final int AI_CHASER = 0;
    static final int AI_SHOOTER = 1;
    static final int AI_CHARGER = 2;
    static final int AI_BOMBER = 3;
    static final int AI_HEALER = 4;
    /**
     * A charger that circles the player for a few random laps before it darts, instead of beelining
     * and dashing the moment it is close like {@link #AI_CHARGER}. RunWorld drives its movement at
     * sub-pixel rates because step() floors every speed the catalog's column can express down to
     * 1px, and a chase slower than 1px is the point. Used by Biji Sawit only.
     */
    static final int AI_ORBIT_CHARGER = 5;

    static final int COMPLETE_FINAL_BOSS = 0;
    static final int COMPLETE_ON_DEATH = 1;
    static final int COMPLETE_KILL_TARGET = 2;

    static final int EFFECT_DAMAGE_PCT = 0;
    static final int EFFECT_HASTE_PCT = 1;
    static final int EFFECT_SPEED_PCT = 2;
    static final int EFFECT_MAX_HP = 3;
    static final int EFFECT_PICKUP_PCT = 4;
    static final int EFFECT_CRIT_PCT = 5;
    static final int EFFECT_REGEN = 6;
    static final int EFFECT_SHIELD = 7;
    static final int EFFECT_NEGATE = 8;
    static final int EFFECT_FOOD_DROP = 9;
    static final int EFFECT_EXP_PCT = 10;
    // Added for the shop passives. EFFECT_DEFENSE_PCT finally feeds the damage-reduction field that
    // RunWorld has always read but never set, and EFFECT_COIN_PCT has no run-item equivalent because
    // item coin rate was never modelled; it drives the enemy coin-drop roll instead.
    static final int EFFECT_DEFENSE_PCT = 11;
    static final int EFFECT_COIN_PCT = 12;
    static final int EFFECT_GROWTH = 13;
    static final int EFFECT_SKILL_UP = 14;

    // ---- upgrade shop groups and purchase kinds ------------------------------------
    // The shop sells three tabs of upgrades, in the same grouping the HoloCure wiki uses:
    // Ability (meta powers), Stat (numeric run bonuses) and Others (challenge flags and
    // services). Each row carries the exact per-level price table from the wiki, so the
    // catalog is both the data and the price list.
    static final int GROUP_ABILITY = 0;
    static final int GROUP_STAT = 1;
    static final int GROUP_OTHERS = 2;

    /**
     * Ordinary levelled purchase: pay the level's price, gain one level, up to maxLevel.
     */
    static final int BUY_LEVEL = 0;
    /**
     * A single flag the shop toggles rather than levels. Hardcore is the only one: buying it
     * flips the same settings bit that Endless Mode reads, so the shop and the run can never
     * disagree about whether hardcore is on.
     */
    static final int BUY_TOGGLE = 1;
    /**
     * A one-shot action with no stored level. Refund All is the only one: it asks for
     * confirmation and then resets every purchase while crediting the full price back.
     */
    static final int BUY_ACTION = 2;

    static final class CharacterDef {
        String id;
        String name;
        int hp;
        int atkPercent;
        int spdPercent;
        int critPercent;
        /**
         * Flat damage reduction this character starts a run with, in percent, before any shop
         * Defense Up is added.
         *
         * <p>Zero for all five, and that is the documented value rather than an unfinished field.
         * The original game ships a "Defense Up" upgrade -- it is in the unpacked string table
         * between "Regeneration" and "Special Attack" -- but has no per-character defence stat:
         * the unpacked symbol table only has AyameDefenseField, which is an ability, and every
         * "reduce damage taken" string belongs to an item or a form. Reduction in the original
         * comes from what the player picks up.
         *
         * <p>It is kept as a field so the shop's Defense Up has an explicit base to stack on top
         * of rather than an implicit one, and so the wiring is testable. See the values in
         * {@link #CHARACTERS} for why it must stay zero without evidence.
         */
        int defensePercent;
        int size;
        String mainWeapon;
        String special;
        String skill;
        boolean playable;
        String assetGroup;
        boolean fallbackArt;
        boolean gameplayApproximation;
    }

    static final int WEAPON_BASIC = 0;
    static final int WEAPON_MAIN = 1;
    static final int WEAPON_COLLAB = 2;
    static final int WEAPON_SUPER_COLLAB = 3;

    static final class WeaponDef {
        String id;
        String name;
        int category; // 0 basic, 1 main, 2 collab, 3 super collab.
        int type;
        int pattern;
        int cooldownMs;
        int damage;
        int count;
        int speed;
        int range;
        int area;
        int knockback;
        int weight;
        int maxLevel;
        String characterOwner; // empty for basic weapons.
        String sprite;
        String prerequisiteA;
        String prerequisiteB;
    }

    static final class ItemDef {
        String id;
        String name;
        int category; // 0 healing, 1 stat, 2 utility, 3 super.
        int effect;
        int value;
        int maxLevel;
        int weight;
        String sprite;
    }

    static final class EnemyDef {
        String id;
        String name;
        int behavior;
        int hp;
        int atk;
        double speed;
        int cooldownMs;
        int exp;
        int coinChance; // 1 in N, 0 = none.
        int collW;
        int collH;
        String sprite;
        boolean documentedBaseStats;
    }

    static final class BossDef {
        String id;
        String name;
        int hp;
        int atk;
        int behavior;
        int cooldownMs;
        int exp;
        String sprite;
        boolean assetApproximation;
        int collW;         // hitbox, matched to the art frame size in RunWorldBossArtTest.
        int collH;
        // True when hp/atk/exp were read off a wiki stage page (see the BOSSES block comment);
        // false for the two original bosses, whose numbers are ours alone. The EnemyDef flag
        // with the same name answers the same "where did these numbers come from" question.
        boolean documentedBaseStats;
    }

    static final class ModeDef {
        String id;
        String name;
        int completion;
        int targetKills;
        boolean allowsHardcore;
        boolean allowsCollabs;
        boolean standardLoadout;
        String objective;
    }

    static final class StageDef {
        String id;
        String name;
        boolean hard;
        boolean timeStage;
        int order;
        String tile;
        int palette;
        String enemyA;
        String enemyB;
        String enemyC;
        int spawnIntervalMs;
        int maxEnemies;
        int coinClear;
        int unlockRequirement; // stage order required; -1 and 99 mean always unlocked (see FullGameCanvas.stageUnlocked).
        String bossA;
        int bossATimeSec;
        String bossB;
        int bossBTimeSec;
        String documentedSource;
        String coverage;
        // Stage-owned playfield size. 0 on either axis means "unspecified": the run then takes
        // that axis from the viewport, which is the historical behaviour for every stage today.
        // The fields exist so world size stops being overwritten by the screen size on every
        // frame (see RunWorld.setViewport); no stage sets a value yet.
        int worldW;
        int worldH;
        // Horizontal playfield width as a multiple of the visible width (Viewport.worldW).
        // 0 means no horizontal scrolling: the world width then comes from worldW above, which
        // in turn falls back to the viewport. A stage only sets this when its playfield is
        // genuinely wider than the screen; the width then adapts to the screen instead of being
        // pinned to one constant, so the world stays wider than the view on any display.
        int scrollW;
        // Composed background grid. When both are above zero the stage draws a fixed COLSxROWS
        // arrangement of images under /assets/fullgame/stages/<tile>_parts/ instead of repeating a
        // single floor tile, which is what a hand-composed arena needs: the reference scene is one
        // picture with a burning border around a clearing, not a repeating pattern. Zero on both
        // axes means the historical behaviour, one repeating tile.
        int mapCols;
        int mapRows;
    }

    static final class CollabDef {
        String id;
        String name;
        String weaponA;
        String weaponB;
        String type;
        String source;
    }

    // Playable roster: only characters with verified local animation assets are exposed.
    // Order is load-bearing: AssetLibrary maps asset groups Gura, Ame, Calli, Kiara, Ina to
    // character indices 0-4 (AssetLibrary.groupIndex), and each entry's mainWeapon must
    // resolve against BASE_WEAPONS for validate() to pass.
    static final CharacterDef[] BASE_CHARACTERS = {
        // Base damage reduction per character, in percent, before any shop Defense Up.
        //
        // Every value is 0, and that is the documented answer rather than a placeholder. The
        // original game has a "Defense Up" upgrade -- it appears verbatim in the unpacked string
        // table between "Regeneration" and "Special Attack" -- but it has no per-character defence
        // stat at all: searching the unpacked symbol table for "defense"/"defence" turns up only
        // AyameDefenseField, which is an ability, and every "reduce damage taken" string belongs
        // to an item or a form. Damage reduction in the original comes from what you pick up, not
        // from who you are.
        //
        // So the field exists to give the shop's Defense Up a base to stack on top of, and it is
        // deliberately left at zero for every character. Inventing per-character numbers here
        // would be unsourced balance masquerading as data, and it would make the larger
        // characters better on two axes at once. Raise these only with evidence, or as a
        // deliberately recorded balance decision of its own.
        character("gura", "Gawr Gura", 65, 110, 140, 5, 1, "trident_thrust", "Shark Call", "Shark Bite", true, "Gura", false, 0),
        character("ame", "Amelia Watson", 75, 130, 135, 10, 3, "pistol_shot", "Slow Time", "Hunter's Instinct", true, "Ame", false, 0),
        character("calli", "Mori Calliope", 70, 115, 130, 10, 4, "scythe_swing", "The Reaper", "Undying", true, "Calli", false, 0),
        character("kiara", "Takanashi Kiara", 90, 100, 140, 5, 3, "phoenix_sword", "Phoenix Fire", "Second Flame", true, "Kiara", false, 0),
        character("ina", "Ninomae Inanis", 75, 90, 150, 1, 1, "summon_tentacle", "Spirit of the Oni", "The Ancient One", true, "Ina", false, 0)
    };

    static final CharacterDef[] CHARACTERS = WikiCatalogData.buildCharacters(BASE_CHARACTERS);

    // Additional documented roster is tracked in data/wiki/characters.csv. Entries without
    // verified local animation assets are defined but not presented as playable.
    // Weapon damage lives in the rows below on the enemy-HP scale: a level-1 main weapon must
    // one-shot the weakest minute-0 fan at its owner's ATK% (Trident 30 x 1.10 = 33 vs Shrimp's
    // 30 HP; the dark variants and every charger take two hits, which is the original's opening
    // too). The table was authored at a tenth of this scale (damage 2-7), which took ten swings
    // to kill one 30 HP Shrimp and made the first minutes of a zero-coin run unwinnable; the
    // 2026-10-20 retune multiplies the damage column by ten -- mains set to land the one-shot
    // floor against each owner's ATK% (Ame 24 x 1.30, Ina 34 x 0.90) -- and leaves cooldown,
    // count, reach, area, knockback, weight and levels untouched. Nothing here is wiki-sourced:
    // enemy HP is approximated (documentedBaseStats = false) and so is this curve; see
    // docs/GAME_MECHANICS.md.
    static final WeaponDef[] BASE_WEAPONS = {
        weapon("trident_thrust", "Trident Thrust", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 900, 30, 1, 0, 34, 18, 3, 3, 7, "gura", "trident", "", ""),
        weapon("pistol_shot", "Pistol Shot", 1, WEAPON_MULTISHOT, PATTERN_PROJECTILE, 800, 24, 3, 9, 110, 8, 1, 3, 7, "ame", "ame_bullet", "", ""),
        weapon("scythe_swing", "Scythe Swing", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 1000, 30, 1, 0, 32, 24, 4, 3, 7, "calli", "scythe", "", ""),
        weapon("phoenix_sword", "Phoenix Sword", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 900, 30, 1, 0, 38, 20, 3, 3, 7, "kiara", "phoenix", "", ""),
        weapon("summon_tentacle", "Summon Tentacle", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 1000, 34, 1, 0, 30, 16, 2, 3, 7, "ina", "tentacle", "", ""),

        weapon("bl_book", "BL Book", 0, WEAPON_MULTISHOT, PATTERN_ORBIT, 900, 20, 2, 4, 34, 10, 2, 3, 7, "", "bullet", "", ""),
        weapon("psycho_axe", "Psycho Axe", 0, WEAPON_RANGED, PATTERN_PROJECTILE, 1200, 30, 1, 6, 130, 12, 2, 3, 7, "", "axe", "", ""),
        weapon("sausage", "Sausage", 0, WEAPON_MELEE, PATTERN_HOMING, 1400, 40, 1, 5, 90, 10, 3, 3, 7, "", "bullet", "", ""),
        weapon("holo_bomb", "Holo Bomb", 0, WEAPON_MULTISHOT, PATTERN_AOE, 1800, 40, 1, 4, 120, 28, 3, 4, 7, "", "bomb", "", ""),
        weapon("glowstick", "Glowstick", 0, WEAPON_MULTISHOT, PATTERN_BOOMERANG, 1100, 20, 1, 7, 100, 8, 1, 4, 7, "", "bullet", "", ""),
        weapon("bounce_ball", "Bounce Ball", 0, WEAPON_MULTISHOT, PATTERN_HOMING, 1600, 40, 1, 5, 150, 24, 4, 4, 7, "", "bomb", "", ""),
        weapon("cutting_board", "Cutting Board", 0, WEAPON_RANGED, PATTERN_WALL, 1500, 30, 1, 5, 60, 22, 2, 2, 7, "", "axe", "", ""),
        weapon("ens_curse", "EN's Curse", 0, WEAPON_MULTISHOT, PATTERN_PROJECTILE, 1300, 20, 3, 7, 105, 10, 1, 2, 7, "", "bullet", "", ""),
        weapon("owl_dagger", "Owl Dagger", 0, WEAPON_MELEE, PATTERN_PIERCING, 600, 20, 1, 10, 85, 8, 1, 2, 7, "", "ame_bullet", "", ""),
        weapon("fan_beam", "Fan Beam", 0, WEAPON_RANGED, PATTERN_PIERCING, 1000, 40, 1, 12, 150, 8, 2, 3, 7, "", "ame_bullet", "", ""),

        weapon("absolute_wall", "Absolute Wall", 2, WEAPON_MELEE, PATTERN_WALL, 1600, 60, 1, 0, 46, 38, 8, 0, 7, "", "axe", "bounce_ball", "cutting_board"),
        weapon("bl_fujoshi", "BL Fujoshi", 2, WEAPON_MELEE, PATTERN_MELEE_ARC, 1300, 60, 1, 0, 42, 30, 7, 0, 7, "", "bullet", "bl_book", "psycho_axe"),
        weapon("black_plague", "Black Plague", 2, WEAPON_MULTISHOT, PATTERN_AOE, 1500, 50, 2, 4, 115, 24, 3, 0, 7, "", "bomb", "ens_curse", "owl_dagger"),
        weapon("blood_lust", "Blood Lust", 2, WEAPON_MELEE, PATTERN_MELEE_ARC, 1000, 70, 2, 0, 48, 30, 8, 0, 7, "", "scythe", "owl_dagger", "psycho_axe"),
        weapon("legendary_sausage", "Legendary Sausage", 2, WEAPON_MELEE, PATTERN_ORBIT, 900, 50, 2, 5, 40, 14, 4, 0, 7, "", "bullet", "bl_book", "sausage")
    };

    static final WeaponDef[] WEAPONS = WikiCatalogData.buildWeapons(BASE_WEAPONS);

    /**
     * The permanent upgrade shop, one row per entry, in the exact grouping and price order of
     * the HoloCure wiki's Upgrade page (Ability, then Stat, then Others).
     *
     * <p>Every row carries its real per-level price table, so the catalog doubles as the price
     * list the wiki documents. Rows fall into two classes:
     *
     * <ul>
     *   <li><b>Wired</b> rows feed a field the run already computes or one this build added for
     *       them: Max HP, ATK, SPD, Crit, Pick Up Range, Haste, Regeneration, Defense, EXP and
     *       Money reach {@code RunWorld.recomputeStats}; Special Attack gates the special;
     *       Reroll and Tahan (Hold) set the per-run charges; Marketing adds spawns; Weapon Limit
     *       and Item Limit shrink the loadout caps; Collab Ban blocks collab offers; Hardcore is
     *       the settings flag; Refund All credits everything back.</li>
     *   <li><b>Locked</b> rows have no gameplay backing yet. They are shown with their wiki
     *       prices so the shop reads like the real thing, but {@code locked} refuses every
     *       purchase: Growth, Eliminate, Stamps, Kustomisasi (Customize), Support, Temuan
     *       Material, Surat Fans, Enchantments, Fandom, Special CDR, Skill Up, Food Drops Up,
     *       Enhancement Rate Up, Supers Ban and G Rank Off.</li>
     * </ul>
     *
     * <p>The skill a character brings is NOT sold here any more: it is always active, granted
     * from the first tick of every run. Order matters only for display and for the nibble each
     * row's level is stored in — {@code SaveStore} keys storage on this array's index, so rows
     * may be appended but never reordered.
     */
    static final UpgradeDef[] UPGRADES = {
        // ---- Ability, wiki order ------------------------------------------------
        upgrade("special_attack", "Special Attack", "Buka Special Attack untuk semua karakter",
                GROUP_ABILITY, -1, 0, false, BUY_LEVEL, new long[] { 500 }),
        upgrade("growth", "Growth", "+1% damage senjata utama & special per level (terkunci)",
                GROUP_ABILITY, EFFECT_GROWTH, 1, false, BUY_LEVEL, new long[] { 500, 10000, 50000 }),
        upgrade("reroll", "Reroll", "+1 Reroll tiap level up per level",
                GROUP_ABILITY, -1, 0, false, BUY_LEVEL,
                new long[] { 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000 }),
        upgrade("eliminate", "Eliminate", "Hapus 1 pilihan tiap level up (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL,
                new long[] { 2500, 5000, 7500, 10000, 12500, 15000, 17500, 20000, 22500, 25000 }),
        upgrade("stamps", "Stamps", "Buka sistem Stamps (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 500 }),
        upgrade("hold", "Tahan (Hold)", "+1 Tahan tiap level up per level",
                GROUP_ABILITY, -1, 0, false, BUY_LEVEL,
                new long[] { 5000, 10000, 15000, 20000, 25000 }),
        upgrade("customize", "Kustomisasi", "Buka Armory: bisa nonaktifkan senjata/item (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 50000 }),
        upgrade("support", "Support", "Buka Support Item (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 1000 }),
        upgrade("material_find", "Temuan Material", "Fan bisa drop material Holo House (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 20000 }),
        upgrade("fan_letters", "Surat Fans", "Buka Surat Fans: boost permanen (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 50000 }),
        upgrade("enchantments", "Enchantments", "Buka Enchantment senjata (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 1000 }),
        upgrade("fandom", "Fandom", "Buka Fandom dari pencapaian (terkunci)",
                GROUP_ABILITY, -1, 0, true, BUY_LEVEL, new long[] { 2000 }),

        // ---- Stat, wiki order ----------------------------------------------------
        upgrade("max_hp", "Max HP Up", "+4% Max HP per level (maks 40%)",
                GROUP_STAT, EFFECT_MAX_HP, 4, false, BUY_LEVEL,
                new long[] { 200, 500, 1200, 2750, 6000, 12000, 18000, 24000, 30000, 36000 }),
        upgrade("atk_up", "ATK Up", "+6% damage per level (maks 60%)",
                GROUP_STAT, EFFECT_DAMAGE_PCT, 6, false, BUY_LEVEL,
                new long[] { 300, 750, 1800, 4100, 9100, 18000, 25000, 31000, 37000, 43000 }),
        upgrade("spd_up", "SPD Up", "+6% kecepatan gerak per level (maks 60%)",
                GROUP_STAT, EFFECT_SPEED_PCT, 6, false, BUY_LEVEL,
                new long[] { 200, 500, 1200, 2750, 6000, 12000, 18000, 24000, 30000, 36000 }),
        upgrade("crit_up", "Crit Up", "+2% critical per level (maks 10%)",
                GROUP_STAT, EFFECT_CRIT_PCT, 2, false, BUY_LEVEL,
                new long[] { 300, 750, 1800, 4100, 9100 }),
        upgrade("pickup_range", "Pick Up Range", "+10% radius item per level (maks 100%)",
                GROUP_STAT, EFFECT_PICKUP_PCT, 10, false, BUY_LEVEL,
                new long[] { 200, 500, 1200, 2750, 6000, 12000, 18000, 24000, 30000, 36000 }),
        upgrade("haste_up", "Haste Up", "+4% attack speed per level (maks 20%)",
                GROUP_STAT, EFFECT_HASTE_PCT, 4, false, BUY_LEVEL,
                new long[] { 400, 1000, 2400, 5500, 12000 }),
        upgrade("regeneration", "Regeneration", "+1 HP tiap 5 detik per level (maks 5 HP)",
                GROUP_STAT, EFFECT_REGEN, 1, false, BUY_LEVEL,
                new long[] { 200, 500, 1200, 2750, 6000 }),
        upgrade("defense_up", "Defense Up", "-3% damage diterima per level (maks 15%)",
                GROUP_STAT, EFFECT_DEFENSE_PCT, 3, false, BUY_LEVEL,
                new long[] { 500, 1250, 3000, 6900, 15000 }),
        upgrade("special_cdr", "Special CDR", "-3% cooldown special per level (terkunci)",
                GROUP_STAT, -1, 0, true, BUY_LEVEL,
                new long[] { 500, 1250, 3000, 6900, 15000 }),
        upgrade("skill_up", "Skill Up", "+4% damage skill per level (terkunci)",
                GROUP_STAT, EFFECT_SKILL_UP, 4, false, BUY_LEVEL,
                new long[] { 500, 1250, 3000, 6900, 15000, 20000, 30000, 40000, 50000, 60000 }),
        upgrade("exp_up", "EXP Gain Up", "+4% EXP per level (maks 20%)",
                GROUP_STAT, EFFECT_EXP_PCT, 4, false, BUY_LEVEL,
                new long[] { 300, 750, 1800, 4100, 9100 }),
        upgrade("food_up", "Food Drops Up", "+4% drop makanan per level (maks 20%)",
                GROUP_STAT, EFFECT_FOOD_DROP, 4, false, BUY_LEVEL,
                new long[] { 150, 375, 900, 2000, 4500 }),
        upgrade("money_up", "Money Gain Up", "+20% nilai koin per level (maks 200%)",
                GROUP_STAT, EFFECT_COIN_PCT, 20, false, BUY_LEVEL,
                new long[] { 400, 1000, 2400, 5500, 12000, 20000, 30000, 40000, 50000, 60000 }),
        upgrade("enhance_rate", "Enhancement Rate Up", "+3% chance enhance per level (terkunci)",
                GROUP_STAT, -1, 0, true, BUY_LEVEL,
                new long[] { 300, 750, 1800, 4100, 9100 }),

        // ---- Others, wiki order ---------------------------------------------------
        upgrade("marketing", "Marketing", "+1 spawn fan tiap interval per level",
                GROUP_OTHERS, -1, 0, false, BUY_LEVEL,
                new long[] { 2000, 4000, 6000, 8000, 10000 }),
        upgrade("weapon_limit", "Weapon Limit", "Batas senjata -1 per level (6 jadi 1)",
                GROUP_OTHERS, -1, 0, false, BUY_LEVEL,
                new long[] { 0, 0, 0, 0, 0 }),
        upgrade("item_limit", "Item Limit", "Batas item -1 per level (6 jadi 0)",
                GROUP_OTHERS, -1, 0, false, BUY_LEVEL,
                new long[] { 0, 0, 0, 0, 0, 0 }),
        upgrade("collab_ban", "Collab Ban", "Collab tidak bisa dibuat",
                GROUP_OTHERS, -1, 0, false, BUY_LEVEL, new long[] { 0 }),
        upgrade("supers_ban", "Supers Ban", "Super item tidak drop (terkunci)",
                GROUP_OTHERS, -1, 0, true, BUY_LEVEL, new long[] { 0 }),
        upgrade("g_rank_off", "G Rank Off", "Matikan bonus G Rank (terkunci)",
                GROUP_OTHERS, -1, 0, true, BUY_LEVEL, new long[] { 0 }),
        upgrade("hardcore", "Hardcore", "HP diatur ke 1 di Endless Mode",
                GROUP_OTHERS, -1, 0, false, BUY_TOGGLE, new long[] { 0 }),
        upgrade("refund_all", "Refund All", "Kembalikan semua pembelian toko",
                GROUP_OTHERS, -1, 0, false, BUY_ACTION, new long[] { 0 })
    };

    /**
     * Price of the next level of an upgrade, i.e. the price of {@code currentLevel + 1}.
     *
     * <p>Prices are read straight from the row's wiki table, so this is a bounds check rather
     * than a curve: a level at or past the cap, a locked row and an unknown row all return
     * {@code -1}, which callers treat as "not purchasable".
     */
    static long upgradeCost(UpgradeDef def, int currentLevel) {
        if (def == null || def.locked || def.prices == null) return -1L;
        if (currentLevel < 0 || currentLevel >= def.prices.length) return -1L;
        return def.prices[currentLevel];
    }

    static UpgradeDef upgrade(String id) {
        for (int i = 0; i < UPGRADES.length; i++) if (UPGRADES[i].id.equals(id)) return UPGRADES[i];
        return null;
    }

    /** Catalog index of one upgrade by id, or -1 when there is no such row. */
    static int upgradeIndex(String id) {
        for (int i = 0; i < UPGRADES.length; i++) if (UPGRADES[i].id.equals(id)) return i;
        return -1;
    }

    static UpgradeDef upgrade(int index) {
        if (index < 0 || index >= UPGRADES.length) return null;
        return UPGRADES[index];
    }

    /** First catalog index belonging to one shop tab. Rows are grouped contiguously. */
    static int upgradeGroupStart(int group) {
        for (int i = 0; i < UPGRADES.length; i++) if (UPGRADES[i].group == group) return i;
        return UPGRADES.length;
    }

    /** How many upgrade rows one shop tab shows. */
    static int upgradeGroupCount(int group) {
        int start = upgradeGroupStart(group);
        int count = 0;
        for (int i = start; i < UPGRADES.length && UPGRADES[i].group == group; i++) count++;
        return count;
    }

    static UpgradeDef upgrade(String id, String name, String desc, int group, int effect,
            int value, boolean locked, int buyKind, long[] prices) {
        UpgradeDef d = new UpgradeDef();
        d.id = id; d.name = name; d.desc = desc; d.group = group;
        d.effect = effect; d.value = value; d.locked = locked; d.buyKind = buyKind;
        d.prices = prices;
        d.maxLevel = prices == null ? 0 : prices.length;
        return d;
    }

    static final ItemDef[] ITEMS = {
        item("face_mask", "Face Mask", 1, EFFECT_DAMAGE_PCT, 30, 1, 1, "bullet"),
        item("knightly_milk", "Knightly Milk", 1, EFFECT_PICKUP_PCT, 30, 3, 1, "bullet"),
        item("energy_drink", "Energy Drink", 1, EFFECT_SPEED_PCT, 40, 3, 2, "bullet"),
        item("just_bandage", "Just Bandage", 0, EFFECT_MAX_HP, 10, 3, 4, "bullet"),
        item("study_glasses", "Study Glasses", 1, EFFECT_EXP_PCT, 10, 5, 3, "bullet"),
        item("sake", "Sake", 1, EFFECT_CRIT_PCT, 5, 3, 4, "bullet"),
        item("gorillas_paw", "Gorilla's Paw", 1, EFFECT_DAMAGE_PCT, 20, 3, 3, "bullet"),
        item("piki_piki", "Piki Piki Piman", 1, EFFECT_MAX_HP, 15, 3, 4, "bullet"),
        item("limiter", "Limiter", 1, EFFECT_PICKUP_PCT, 100, 3, 4, "bullet"),
        item("headphones", "Headphones", 2, EFFECT_NEGATE, 15, 5, 4, "bullet")
    };

    /**
     * Enemy roster. Every row's {@code documentedBaseStats} is false, and that flag is the honest
     * answer to "where did these numbers come from".
     *
     * <p>There is no source for them. The original ships a "Defense Up" upgrade but no per-character
     * defence stat, and -- the same problem here -- the wiki's Enemy page carries no HP or ATK
     * column; {@code data/wiki/bosses.csv} says so outright for the bosses ("individual boss HP/ATK/
     * EXP values are not on the Enemy page"). There is no {@code enemies.csv} because there was
     * nothing to extract into one. The unpacked tree under {@code ~/Projects/HoloCure_Unpacked} does
     * not help either: {@code binary_logic/} holds script and object <em>names</em> only, with no
     * decompiled GML and no numeric literals to read.
     *
     * <p>The bosses later got their figures from a second place: each stage page's "Enemy
     * Composition" table lists HP/ATK/EXP per boss, so the BOSSES rows below are wiki-sourced
     * and flagged {@code documentedBaseStats} true. Nothing comparable has been extracted for
     * minions, so these rows stay false.
     *
     * <p>So hp/atk here are balance numbers we chose, which is why they are flagged rather than
     * dressed up as data. They read 3..8 for mobs against character HP pools of 65..90 that
     * <em>are</em> documented (boss ATK now reads 15..48 from the wiki).
     *
     * <p>These were briefly halved on the grounds that a boss hit of 48 against a 65 HP character
     * removed three quarters of the pool in one touch. That reasoning turned out to rest on a bug
     * rather than on the numbers: character ATK was being applied as a bonus instead of as the
     * multiplier the table records, so every character was hitting for roughly twice what their
     * own row said. With that fixed, the original values are the right ones and they were restored.
     * The lesson worth keeping is that the two are not independent -- tuning one against the other
     * before either is correct just moves the problem.
     *
     * <p>If real figures ever surface, replace them and flip {@code documentedBaseStats} to true;
     * do not quietly tune these and leave the flag false.
     */
    static final EnemyDef[] ENEMIES = {
        enemy("shrimp", "Shrimp", AI_CHASER, 30, 4, 0.40, 900, 7, 90, 20, 18, "Shrimp", false),
        enemy("dark_shrimp", "Dark Shrimp", AI_CHASER, 48, 5, 0.48, 800, 9, 75, 20, 18, "Shrimp2", false),
        enemy("sukonbu", "Sukonbu", AI_BOMBER, 42, 5, 0.42, 1100, 9, 80, 20, 20, "Sukonbu", false),
        enemy("kuro_sukonbu", "Kuro Sukonbu", AI_BOMBER, 62, 6, 0.48, 950, 11, 65, 20, 20, "Sukonbu2", false),
        enemy("matsurisu", "Matsurisu", AI_CHARGER, 38, 5, 0.50, 850, 10, 85, 22, 18, "Matsurisu", false),
        enemy("matsurisu3", "Matsurisu 3", AI_CHARGER, 60, 7, 0.58, 750, 14, 65, 22, 18, "Matsurisu3", false),
        enemy("moonafic", "Moonafic", AI_SHOOTER, 52, 5, 0.38, 1200, 12, 80, 18, 20, "Moonafic", false),
        enemy("moonafic_vip", "Moonafic VIP", AI_SHOOTER, 78, 7, 0.44, 1000, 16, 60, 18, 20, "Moonafic2", false),
        enemy("nousagi_a", "Nousagi A", AI_CHASER, 45, 6, 0.52, 800, 12, 85, 18, 16, "NousagiA", false),
        enemy("nousagi_b", "Nousagi B", AI_SHOOTER, 58, 6, 0.46, 1050, 15, 75, 18, 16, "NousagiB", false),
        enemy("nousagi_c", "Nousagi C", AI_CHARGER, 66, 7, 0.62, 700, 18, 65, 18, 16, "NousagiC", false),
        enemy("nousagi_d", "Nousagi D", AI_BOMBER, 72, 8, 0.40, 950, 20, 60, 18, 16, "NousagiD", false),
        enemy("otaku", "Otaku", AI_HEALER, 90, 6, 0.34, 1400, 20, 60, 22, 28, "Otaku", false),
        // Original design: small palm seed minion, fragile but persistent. It orbits the player
        // for a few random laps before darting in (AI_ORBIT_CHARGER): the frond crown flares as it
        // closes, which reads as a wind-up. atk 3 and speed 0.48 are a deliberate nerf from the
        // first pass (4 / 0.55): the swarm fills the arena, so per-enemy pressure has to stay low
        // enough that the stage is playable from the moment it is unlocked.
        enemy("biji_sawit", "Biji Sawit", AI_ORBIT_CHARGER, 35, 3, 0.48, 800, 10, 80, 18, 16, "BijiSawit", false),
        // Original design: 19 Kucing, the frantic fan searching for his missing cat. Walks
        // straight at the player (AI_CHASER) with mid-table stats, so the Hutan Sawit swarm
        // gains a second silhouette without changing the stage's difficulty profile. The art
        // frame is 14x32; the hitbox is 12x28 so the thin legs at the bottom do not count
        // as body.
        enemy("19kucing", "19 Kucing", AI_CHASER, 40, 4, 0.50, 800, 12, 75, 12, 28, "Kucing19", false)
    };

    static final BossDef[] BOSSES = {
        // HP, ATK and EXP below are the wiki's own figures, read off each stage page's
        // "Enemy Composition" table (2026-10-20), e.g.
        // https://holocure.wiki.gg/wiki/Stage_2_-_Holo_Office for Mikodanye and A-chan.
        // The old comment here claimed no such figures existed; that was true of the wiki's
        // Enemy page and of the extracted data/wiki/bosses.csv (which has no HP column at
        // all), but every stage page carries HP/ATK/SPD/EXP per boss. documentedBaseStats
        // records the result: true on every row below except the two originals.
        //
        // One row per appearance: a boss that spawns on both a Normal and a Hard page gets a
        // second *_hard row, because the wiki gives each page its own numbers (Fubuzilla is
        // 8000 on Stage 1 but 15000 on Stage 1 (Hard)). Stage tables and hardSecondBoss pick
        // the row for the stage they spawn in.
        //
        // Area 15 (three sprites) and Halloween Myth (five members) spawn here as a single
        // entity, so their rows carry the mean of the members' wiki stats rather than the
        // group total; the comment on each row spells the members out.
        //
        // collW/collH are the frame size of each boss's art directory (see
        // AssetLibrary.bossDirs and RunWorldBossArtTest, which pins the two
        // together), so the hitbox covers the sprite instead of one fixed
        // 42x42 corner of it.
        // Stage 1: Fubuzilla 10:00, Smol Ame 20:00 (Normal) / both at 10:00 (Hard).
        boss("fubuzilla", "Fubuzilla", 8000, 15, AI_SHOOTER, 1400, 2000, "fubuzilla", false, 64, 55, true),
        boss("fubuzilla_hard", "Fubuzilla", 15000, 22, AI_SHOOTER, 1400, 1500, "fubuzilla", false, 64, 55, true),
        boss("smol_ame", "Smol Ame", 25000, 20, AI_CHASER, 1000, 5000, "AchanBoss", true, 61, 84, true),
        boss("smol_ame_hard", "Smol Ame", 12000, 20, AI_CHASER, 1000, 1500, "AchanBoss", true, 61, 84, true),
        // Stage 2: Mikodanye 10:00, A-chan 20:00 (Normal); the Hard page schedules Mikodanye
        // and A-chan together at 10:00, but only Mikodanye is spawned by this build's schedule.
        boss("mikodanye", "Mikodanye", 16000, 16, AI_SHOOTER, 1300, 2200, "Mikodanye", false, 51, 67, true),
        boss("mikodanye_hard", "Mikodanye", 15000, 18, AI_SHOOTER, 1300, 2000, "Mikodanye", false, 51, 67, true),
        boss("achan", "A-chan", 45000, 20, AI_CHARGER, 850, 6000, "AchanBoss", false, 24, 29, true),
        // Stage 3: Shubangelion 10:00, Spiderchama 20:00 (Normal) / both at 10:00 (Hard).
        boss("shubangelion", "Shubangelion", 24000, 22, AI_CHARGER, 1200, 2000, "Shubangelion_walk", false, 68, 101, true),
        boss("shubangelion_hard", "Shubangelion", 35000, 30, AI_CHARGER, 1200, 2000, "Shubangelion_walk", false, 68, 101, true),
        boss("spiderchama", "Spiderchama", 55000, 25, AI_SHOOTER, 1000, 6000, "Pekodam", true, 128, 69, true),
        // Stage 4: Udin 10:00, Area 15 20:00 (Normal); the Hard page schedules Udin and
        // Area 15 together at 10:00, but only Udin is spawned by this build's schedule.
        boss("udin", "Udin", 32000, 22, AI_SHOOTER, 1100, 2000, "Udin_walk", false, 43, 53, true),
        boss("udin_hard", "Udin", 60000, 35, AI_SHOOTER, 1100, 2000, "Udin_walk", false, 43, 53, true),
        // Area 15 is three sprites on Stage 4: Moontato 53000/24, Risusaurus 57000/20 and
        // UFOFI 55000/25 (EXP 2000 each). Mean: 55000 HP, 23 ATK, 2000 EXP.
        boss("area15", "Area 15", 55000, 23, AI_BOMBER, 1200, 2000, "Pekodam", true, 83, 67, true),
        // Stage 5: Nanora 10:00, Pekodam 20:00.
        boss("nanora", "Nanora", 50000, 27, AI_CHASER, 900, 1400, "Moonafic2", true, 25, 28, true),
        boss("pekodam", "Pekodam", 100000, 35, AI_SHOOTER, 850, 3000, "Pekodam", false, 99, 116, true),
        // Hard-only finals, from the Stage 1 (Hard), Stage 2 (Hard) and Stage 4 (Hard) pages.
        boss("halloween_bae", "Halloween Bae", 72000, 27, AI_BOMBER, 1000, 6000, "Pekodam", true, 78, 70, true),
        boss("nodoka", "Harusaki Nodoka", 90000, 30, AI_SHOOTER, 950, 6000, "Mikodanye", true, 22, 33, true),
        // Halloween Myth is five members spawning together on Stage 3 (Hard): Dino Gura
        // 78000/35, Nurse Calli 83000/28, Dr. Oopsie 80000/28, Vampire Kiara 82000/33 and
        // Pumpkin Ame 75000/30 (EXP 2000 each except Pumpkin Ame 5000). Means: 79600 HP,
        // 30.8 -> 31 ATK, 2600 EXP.
        boss("halloween_myth", "Halloween Myth", 79600, 31, AI_CHARGER, 1000, 2600, "Pekodam", true, 66, 79, true),
        boss("goriela", "Goriela", 180000, 47, AI_SHOOTER, 900, 3000, "Pekodam", true, 98, 75, true),
        // Original design: WoWo, guardian of the burnt palm grove. Keeps its distance
        // and fires a widening radial seed burst, so the fight is about dodging
        // rings rather than chasing a single bullet. Tuned above Pekodam, the final
        // boss of the stage this one unlocks behind, on both axes: it closes the
        // normal stage list and must not feel like a step back. Pekodam now carries
        // its wiki 100000/35, so WoWo sits above that; EXP follows the 300/400 pair
        // scaled up to the wiki's boss-EXP range. documentedBaseStats stays false:
        // this row is ours alone.
        boss("wowo", "WoWo", 110000, 44, AI_SHOOTER, 1000, 3000, "WoWo", false, 64, 64, false),
        // Original design: FufuFafa, the second Arena Hutan Sawit boss, scheduled for the
        // 20:00 encounter so the stage closes on a new fight instead of a repeat WoWo. Same
        // radial-burst family as WoWo but on a tighter cooldown and a step up on both HP and
        // ATK. collW/collH track the 46x56 frame size of bosses/FufuFafa.
        boss("fufufafa", "FufuFafa", 125000, 48, AI_SHOOTER, 900, 4000, "FufuFafa", false, 46, 56, false)
    };

    static final ModeDef[] MODES = {
        mode("stage", "Stage Mode", COMPLETE_FINAL_BOSS, 0, false, true, false, "Defeat the stage's final boss"),
        mode("endless", "Endless Mode", COMPLETE_ON_DEATH, 0, true, true, false, "Survive until defeated"),
        mode("time", "Time Mode", COMPLETE_KILL_TARGET, 4000, false, false, true, "Defeat 4000 fans as quickly as possible")
    };

    static final StageDef[] STAGES = {
        stage("stage1", "Grassy Plains", false, false, 0, "grass_field", 0, "shrimp", "dark_shrimp", "shrimp", 1300, 20, 3000, -1, "fubuzilla", 600, "smol_ame", 1200, "https://holocure.wiki.gg/wiki/Stage_1_-_Grassy_Plains", "documented stage/boss timing and boss stats; minion combat values approximated"),
        stage("stage2", "Holo Office", false, false, 1, "office", 1, "sukonbu", "kuro_sukonbu", "sukonbu", 1150, 24, 5000, 0, "mikodanye", 600, "achan", 1200, "https://holocure.wiki.gg/wiki/Stage_2_-_Holo_Office", "documented stage/boss timing and boss stats; minion combat values approximated"),
        stage("stage3", "Halloween Castle", false, false, 2, "castle", 2, "matsurisu", "matsurisu3", "matsurisu", 1000, 28, 7500, 1, "shubangelion", 600, "spiderchama", 1200, "https://holocure.wiki.gg/wiki/Stage_3_-_Halloween_Castle", "documented stage/boss timing and boss stats; Spiderchama asset approximated"),
        stage("stage4", "Gelora Bung Yagoo", false, false, 3, "carpet", 3, "moonafic", "moonafic_vip", "moonafic", 900, 32, 10000, 2, "udin", 600, "area15", 1200, "https://holocure.wiki.gg/wiki/Stage_4_-_Gelora_Bung_Yagoo", "documented stage/boss timing and boss stats; Area 15 asset approximated"),
        // unlockRequirement 99 marks this stage always unlocked. Verified from the reference JAR
        // bytecode: the stage5 entry pushes interval 850, cap 36, coins 15000, then 99, then the
        // boss string "nanora". FullGameCanvas.stageUnlocked treats 99 exactly like -1 and returns
        // true without consulting the save, so Fantasy Island is never locked in the reference.
        stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, "nousagi_a", "nousagi_b", "nousagi_c", 850, 36, 15000, 99, "nanora", 600, "pekodam", 1200, "https://holocure.wiki.gg/wiki/Stage_5_-_Fantasy_Island", "documented stage/boss timing and boss stats; Nanora asset approximated"),
        stage("stage1h", "Grassy Plains (Night)", true, false, 5, "grass", 5, "dark_shrimp", "shrimp", "dark_shrimp", 1000, 26, 5000, 1, "fubuzilla_hard", 600, "halloween_bae", 1200, "https://holocure.wiki.gg/wiki/Stage_1_(Hard)_-_Grassy_Plains_(Night)", "Hard variant documented; 20m final boss uses fallback asset"),
        stage("stage2h", "Holo Office (Evening)", true, false, 6, "office", 6, "kuro_sukonbu", "sukonbu", "kuro_sukonbu", 900, 30, 15000, 5, "mikodanye_hard", 600, "nodoka", 1200, "https://holocure.wiki.gg/wiki/Stage_2_(Hard)_-_Holo_Office_(Evening)", "Hard variant documented; 20m Nodoka uses fallback asset"),
        stage("stage3h", "Halloween Castle (Myth)", true, false, 7, "castle", 7, "matsurisu3", "matsurisu", "matsurisu3", 850, 34, 30000, 6, "shubangelion_hard", 600, "halloween_myth", 1200, "https://holocure.wiki.gg/wiki/Stage_3_(Hard)_-_Halloween_Castle_(Myth)", "Hard variant documented; 20m Halloween Myth uses fallback asset"),
        stage("stage4h", "Gelora Bung Yagoo (Night)", true, false, 8, "yagoo", 8, "moonafic_vip", "moonafic", "moonafic_vip", 800, 38, 50000, 7, "udin_hard", 600, "goriela", 1200, "https://holocure.wiki.gg/wiki/Stage_4_(Hard)_-_Gelora_Bung_Yagoo_(Night)", "Hard variant documented; 20m Goriela uses fallback asset"),
        stage("time1", "Concert Stage", false, true, 9, "concert", 9, "otaku", "otaku", "otaku", 650, 40, 0, 2, "", 0, "", 0, "https://holocure.wiki.gg/wiki/Stage", "Time Mode target 4000; collabs and super items disabled"),
        // Original custom stage: Arena Hutan Sawit. Not from the reference game: its enemy, boss,
        // tile and combat values are this project's own design. It is placed last so its order stays
        // equal to its catalog index, the invariant StageCompletionUnlockTest guards, because
        // completion is recorded under .order while the unlock table is keyed by order too.
        //
        // unlockRequirement 99 makes it always available, the same marker Fantasy Island uses and the
        // one FullGameCanvas.stageUnlocked treats exactly like -1. It was originally gated behind
        // Fantasy Island (order 4) so the stage would sit at the end of the normal progression; that
        // gate is commented out below and only needs the literal restored to come back.
        // stage("stage6", ... 4, ...)   <- the intended gate
        stage("stage6", "Arena Hutan Sawit", false, false, 10, "palm_forest", 10, "biji_sawit", "19kucing", "biji_sawit", 1400, 18, 12000, 99, "wowo", 600, "fufufafa", 1200, "", "Original custom stage; art from tools/generate_palm_forest_sprites.py, tools/generate_palm_forest_map.py and tools/prepare_hutan_sawit_cast.py; combat values are original and unvalidated", 4, 4)
    };

    static {
        // Holo Office is the one stage whose playfield is wider than the screen. The source room
        // (HoloCure_Unpacked/index/Rooms_Detail.txt, rm_HoloOffice) is 6400x2248, i.e. far wider
        // than tall, which is why this stage scrolls sideways only. That original width cannot be
        // used literally: 6400 px does not fit a 320 px screen and MIDP 2.0 cannot scale images at
        // runtime, so the world is expressed as a multiple of the visible width instead. Two
        // screens of horizontal travel is the smallest value that still gives a real exploration
        // area while keeping the world twice, not many times, the view. stage2h (Evening) is
        // deliberately left alone and keeps the viewport-sized world.
        for (int i = 0; i < STAGES.length; i++) if ("stage2".equals(STAGES[i].id)) STAGES[i].scrollW = 2;
    }

    /**
 * One permanent upgrade sold by the shop.
 *
 * <p>A wired row is a single stat fed by one integer {@code value} per level, and every
 * {@code effect} maps to a field RunWorld already computes, so a wired row needs no new damage or
 * pickup code path. A locked row carries {@code effect = -1}: it is data for the shop page only
 * until the gameplay behind it exists. Levels are global and read at the start of every run.
 *
 * <p>{@code prices} is the row's wiki price table for levels 1..maxLevel; {@code maxLevel} is
 * simply its length, so a row can never advertise more levels than it has prices for.
 */
    static final class UpgradeDef {
        String id;
        String name;
        String desc;     // one-line effect text shown on the shop detail panel.
        int group;       // GROUP_ABILITY / GROUP_STAT / GROUP_OTHERS.
        int effect;      // one of the EFFECT_* constants, or -1 when the row has no run effect.
        int value;       // per level, in the same unit the effect is expressed in.
        int maxLevel;
        boolean locked;  // shown with its prices, but no purchase may ever land.
        int buyKind;     // BUY_LEVEL / BUY_TOGGLE / BUY_ACTION.
        long[] prices;   // price of level 1..maxLevel, from the HoloCure wiki tables.
    }

    static final CollabDef[] COLLABS;

    static {
        java.io.InputStream is = ContentCatalog.class.getResourceAsStream("/data/wiki/collabs.csv");
        CollabDef[] defs = null;
        if (is != null) {
            try {
                defs = WikiCatalogData.buildCollabDefs(is);
            } catch (Throwable t) {
                // Fallback if CSV parsing fails. This catches Throwable, not just
                // IOException, because it runs in a static initializer: an Error
                // thrown here (a missing class, a VerifyError) would escape as an
                // ExceptionInInitializerError and kill the MIDlet before the first
                // frame, which the IOException-only version could not prevent.
                defs = new CollabDef[] {
                    collab("absolute_wall", "Absolute Wall", "bounce_ball", "cutting_board", "collab", ""),
                    collab("bl_fujoshi", "BL Fujoshi", "bl_book", "psycho_axe", "collab", ""),
                    collab("black_plague", "Black Plague", "ens_curse", "owl_dagger", "collab", ""),
                    collab("blood_lust", "Blood Lust", "owl_dagger", "psycho_axe", "collab", ""),
                    collab("legendary_sausage", "Legendary Sausage", "bl_book", "sausage", "collab", "")
                };
            }
        }
        COLLABS = defs != null ? defs : new CollabDef[] {
            collab("absolute_wall", "Absolute Wall", "bounce_ball", "cutting_board", "collab", ""),
            collab("bl_fujoshi", "BL Fujoshi", "bl_book", "psycho_axe", "collab", ""),
            collab("black_plague", "Black Plague", "ens_curse", "owl_dagger", "collab", ""),
            collab("blood_lust", "Blood Lust", "owl_dagger", "psycho_axe", "collab", ""),
            collab("legendary_sausage", "Legendary Sausage", "bl_book", "sausage", "collab", "")
        };
    }

    static CharacterDef character(String id, String name, int hp, int atk, int spd, int crit,
            int size, String weapon, String special, String skill, boolean playable, String group) {
        return character(id, name, hp, atk, spd, crit, size, weapon, special, skill, playable, group,
                false, 0);
    }

    static CharacterDef character(String id, String name, int hp, int atk, int spd, int crit,
            int size, String weapon, String special, String skill, boolean playable, String group,
            boolean fallbackArt) {
        return character(id, name, hp, atk, spd, crit, size, weapon, special, skill, playable,
                group, fallbackArt, 0);
    }

    static CharacterDef character(String id, String name, int hp, int atk, int spd, int crit,
            int size, String weapon, String special, String skill, boolean playable, String group,
            boolean fallbackArt, int defensePercent) {
        CharacterDef d = new CharacterDef();
        d.id = id; d.name = name; d.hp = hp; d.atkPercent = atk; d.spdPercent = spd;
        d.critPercent = crit; d.size = size; d.mainWeapon = weapon; d.special = special;
        d.skill = skill; d.playable = playable; d.assetGroup = group;
        d.fallbackArt = fallbackArt; d.gameplayApproximation = fallbackArt;
        d.defensePercent = defensePercent;
        return d;
    }

    static WeaponDef weapon(String id, String name, int category, int type, int pattern,
            int cooldown, int damage, int count, int speed, int range, int area,
            int knockback, int weight, int maxLevel, String owner, String sprite, String preA, String preB) {
        WeaponDef d = new WeaponDef();
        d.id = id; d.name = name; d.category = category; d.type = type; d.pattern = pattern;
        d.cooldownMs = cooldown; d.damage = damage; d.count = count; d.speed = speed;
        d.range = range; d.area = area; d.knockback = knockback; d.weight = weight;
        d.maxLevel = maxLevel; d.characterOwner = owner; d.sprite = sprite;
        d.prerequisiteA = preA; d.prerequisiteB = preB;
        return d;
    }

    static ItemDef item(String id, String name, int category, int effect, int value,
            int maxLevel, int weight, String sprite) {
        ItemDef d = new ItemDef();
        d.id = id; d.name = name; d.category = category; d.effect = effect;
        d.value = value; d.maxLevel = maxLevel; d.weight = weight; d.sprite = sprite;
        return d;
    }

    static EnemyDef enemy(String id, String name, int behavior, int hp, int atk, double speed,
            int cooldown, int exp, int coinChance, int w, int h, String sprite, boolean documented) {
        EnemyDef d = new EnemyDef();
        d.id = id; d.name = name; d.behavior = behavior; d.hp = hp; d.atk = atk;
        d.speed = speed; d.cooldownMs = cooldown; d.exp = exp; d.coinChance = coinChance;
        d.collW = w; d.collH = h; d.sprite = sprite; d.documentedBaseStats = documented;
        return d;
    }

    static BossDef boss(String id, String name, int hp, int atk, int behavior,
            int cooldown, int exp, String sprite, boolean approximation, int collW, int collH,
            boolean documented) {
        BossDef d = new BossDef();
        d.id = id; d.name = name; d.hp = hp; d.atk = atk; d.behavior = behavior;
        d.cooldownMs = cooldown; d.exp = exp; d.sprite = sprite;
        d.assetApproximation = approximation; d.collW = collW; d.collH = collH;
        d.documentedBaseStats = documented;
        return d;
    }

    static ModeDef mode(String id, String name, int completion, int kills, boolean hardcore,
            boolean collabs, boolean standard, String objective) {
        ModeDef d = new ModeDef();
        d.id = id; d.name = name; d.completion = completion; d.targetKills = kills;
        d.allowsHardcore = hardcore; d.allowsCollabs = collabs;
        d.standardLoadout = standard; d.objective = objective;
        return d;
    }

    static StageDef stage(String id, String name, boolean hard, boolean time, int order,
            String tile, int palette, String enemyA, String enemyB, String enemyC,
            int interval, int cap, int coins, int unlock, String bossA, int timeA,
            String bossB, int timeB, String source, String coverage) {
        return stage(id, name, hard, time, order, tile, palette, enemyA, enemyB, enemyC,
            interval, cap, coins, unlock, bossA, timeA, bossB, timeB, source, coverage, 0, 0);
    }

    /** Overload for a stage whose background is a composed grid rather than one repeating tile. */
    static StageDef stage(String id, String name, boolean hard, boolean time, int order,
            String tile, int palette, String enemyA, String enemyB, String enemyC,
            int interval, int cap, int coins, int unlock, String bossA, int timeA,
            String bossB, int timeB, String source, String coverage, int mapCols, int mapRows) {
        StageDef d = new StageDef();
        d.id = id; d.name = name; d.hard = hard; d.timeStage = time; d.order = order;
        d.tile = tile; d.palette = palette; d.enemyA = enemyA; d.enemyB = enemyB;
        d.enemyC = enemyC; d.spawnIntervalMs = interval; d.maxEnemies = cap;
        d.coinClear = coins; d.unlockRequirement = unlock; d.bossA = bossA;
        d.bossATimeSec = timeA; d.bossB = bossB; d.bossBTimeSec = timeB;
        d.documentedSource = source; d.coverage = coverage;
        d.mapCols = mapCols; d.mapRows = mapRows;
        return d;
    }

    static CollabDef collab(String id, String name, String a, String b, String type, String source) {
        CollabDef d = new CollabDef();
        d.id = id; d.name = name; d.weaponA = a; d.weaponB = b;
        d.type = type; d.source = source;
        return d;
    }

    static CharacterDef character(int index) {
        return CHARACTERS[index];
    }

    static WeaponDef weapon(int index) { return WEAPONS[index]; }
    static WeaponDef weapon(String id) {
        for (int i = 0; i < WEAPONS.length; i++) if (WEAPONS[i].id.equals(id)) return WEAPONS[i];
        return null;
    }
    static int weaponIndex(String id) {
        for (int i = 0; i < WEAPONS.length; i++) if (WEAPONS[i].id.equals(id)) return i;
        return -1;
    }
    static ItemDef item(int index) { return ITEMS[index]; }
    static ItemDef item(String id) {
        for (int i = 0; i < ITEMS.length; i++) if (ITEMS[i].id.equals(id)) return ITEMS[i];
        return null;
    }
    static EnemyDef enemy(String id) {
        for (int i = 0; i < ENEMIES.length; i++) if (ENEMIES[i].id.equals(id)) return ENEMIES[i];
        return null;
    }
    static BossDef boss(String id) {
        for (int i = 0; i < BOSSES.length; i++) if (BOSSES[i].id.equals(id)) return BOSSES[i];
        return null;
    }
    static StageDef stage(int index) { return STAGES[index]; }
    static ModeDef mode(int index) { return MODES[index]; }

    /** Returns null when valid, otherwise a stable validation error. */
    static String validate() {
        for (int i = 0; i < CHARACTERS.length; i++) {
            CharacterDef c = CHARACTERS[i];
            if (weapon(c.mainWeapon) == null) return "character:" + c.id + ":main_weapon_missing";
            if (!c.playable) return "character:" + c.id + ":incomplete_definition_visible";
        }
        for (int i = 0; i < WEAPONS.length; i++) {
            WeaponDef w = WEAPONS[i];
            if (w.category == 2) {
                if (weapon(w.prerequisiteA) == null || weapon(w.prerequisiteB) == null)
                    return "weapon:" + w.id + ":invalid_prerequisite";
                CollabDef c = null;
                for (int j = 0; j < COLLABS.length; j++) if (COLLABS[j].id.equals(w.id)) c = COLLABS[j];
                if (c == null) return "weapon:" + w.id + ":missing_collab_definition";
            }
        }
        for (int i = 0; i < STAGES.length; i++) {
            StageDef s = STAGES[i];
            if (enemy(s.enemyA) == null || enemy(s.enemyB) == null || enemy(s.enemyC) == null)
                return "stage:" + s.id + ":invalid_enemy_reference";
            if (!s.timeStage && (boss(s.bossA) == null || boss(s.bossB) == null))
                return "stage:" + s.id + ":invalid_boss_reference";
        }
        return null;
    }
}
