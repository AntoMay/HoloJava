package me.holojava.fullgame;
import java.io.IOException;

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

    /**
     * How many times a character's skill can be taken from the level-up screen.
     *
     * <p>The skill used to be a one-shot unlock, so a second skill offer could never appear and the
     * offer mix starved the stat branch. Taking it again now raises its power instead of being
     * wasted, which is what makes repeating the skill slot worth a level.
     */
    static final int SKILL_MAX_STACKS = 5;

    static final class CharacterDef {
        String id;
        String name;
        int hp;
        int atkPercent;
        int spdPercent;
        int critPercent;
        int size;
        String mainWeapon;
        String special;
        String skill;
        boolean playable;
        String assetGroup;
        boolean fallbackArt;
        boolean gameplayApproximation;
    }

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
        character("gura", "Gawr Gura", 65, 110, 140, 5, 1, "trident_thrust", "Shark Call", "Shark Bite", true, "Gura"),
        character("ame", "Amelia Watson", 75, 130, 135, 10, 3, "pistol_shot", "Slow Time", "Hunter's Instinct", true, "Ame"),
        character("calli", "Mori Calliope", 70, 115, 130, 10, 4, "scythe_swing", "The Reaper", "Undying", true, "Calli"),
        character("kiara", "Takanashi Kiara", 90, 100, 140, 5, 3, "phoenix_sword", "Phoenix Fire", "Second Flame", true, "Kiara"),
        character("ina", "Ninomae Inanis", 75, 90, 150, 1, 1, "summon_tentacle", "Spirit of the Oni", "The Ancient One", true, "Ina")
    };

    static final CharacterDef[] CHARACTERS = WikiCatalogData.buildCharacters(BASE_CHARACTERS);

    // Additional documented roster is tracked in data/wiki/characters.csv. Entries without
    // verified local animation assets are defined but not presented as playable.
    static final WeaponDef[] BASE_WEAPONS = {
        weapon("trident_thrust", "Trident Thrust", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 900, 3, 1, 0, 34, 18, 3, 3, 7, "gura", "trident", "", ""),
        weapon("pistol_shot", "Pistol Shot", 1, WEAPON_MULTISHOT, PATTERN_PROJECTILE, 800, 2, 3, 9, 110, 8, 1, 3, 7, "ame", "ame_bullet", "", ""),
        weapon("scythe_swing", "Scythe Swing", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 1000, 3, 1, 0, 32, 24, 4, 3, 7, "calli", "scythe", "", ""),
        weapon("phoenix_sword", "Phoenix Sword", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 900, 3, 1, 0, 38, 20, 3, 3, 7, "kiara", "phoenix", "", ""),
        weapon("summon_tentacle", "Summon Tentacle", 1, WEAPON_MELEE, PATTERN_MELEE_ARC, 1000, 3, 1, 0, 30, 16, 2, 3, 7, "ina", "tentacle", "", ""),

        weapon("bl_book", "BL Book", 0, WEAPON_MULTISHOT, PATTERN_ORBIT, 900, 2, 2, 4, 34, 10, 2, 3, 7, "", "bullet", "", ""),
        weapon("psycho_axe", "Psycho Axe", 0, WEAPON_RANGED, PATTERN_PROJECTILE, 1200, 3, 1, 6, 130, 12, 2, 3, 7, "", "axe", "", ""),
        weapon("sausage", "Sausage", 0, WEAPON_MELEE, PATTERN_HOMING, 1400, 4, 1, 5, 90, 10, 3, 3, 7, "", "bullet", "", ""),
        weapon("holo_bomb", "Holo Bomb", 0, WEAPON_MULTISHOT, PATTERN_AOE, 1800, 4, 1, 4, 120, 28, 3, 4, 7, "", "bomb", "", ""),
        weapon("glowstick", "Glowstick", 0, WEAPON_MULTISHOT, PATTERN_BOOMERANG, 1100, 2, 1, 7, 100, 8, 1, 4, 7, "", "bullet", "", ""),
        weapon("bounce_ball", "Bounce Ball", 0, WEAPON_MULTISHOT, PATTERN_HOMING, 1600, 4, 1, 5, 150, 24, 4, 4, 7, "", "bomb", "", ""),
        weapon("cutting_board", "Cutting Board", 0, WEAPON_RANGED, PATTERN_WALL, 1500, 3, 1, 5, 60, 22, 2, 2, 7, "", "axe", "", ""),
        weapon("ens_curse", "EN's Curse", 0, WEAPON_MULTISHOT, PATTERN_PROJECTILE, 1300, 2, 3, 7, 105, 10, 1, 2, 7, "", "bullet", "", ""),
        weapon("owl_dagger", "Owl Dagger", 0, WEAPON_MELEE, PATTERN_PIERCING, 600, 2, 1, 10, 85, 8, 1, 2, 7, "", "ame_bullet", "", ""),
        weapon("fan_beam", "Fan Beam", 0, WEAPON_RANGED, PATTERN_PIERCING, 1000, 4, 1, 12, 150, 8, 2, 3, 7, "", "ame_bullet", "", ""),

        weapon("absolute_wall", "Absolute Wall", 2, WEAPON_MELEE, PATTERN_WALL, 1600, 6, 1, 0, 46, 38, 8, 0, 7, "", "axe", "bounce_ball", "cutting_board"),
        weapon("bl_fujoshi", "BL Fujoshi", 2, WEAPON_MELEE, PATTERN_MELEE_ARC, 1300, 6, 1, 0, 42, 30, 7, 0, 7, "", "bullet", "bl_book", "psycho_axe"),
        weapon("black_plague", "Black Plague", 2, WEAPON_MULTISHOT, PATTERN_AOE, 1500, 5, 2, 4, 115, 24, 3, 0, 7, "", "bomb", "ens_curse", "owl_dagger"),
        weapon("blood_lust", "Blood Lust", 2, WEAPON_MELEE, PATTERN_MELEE_ARC, 1000, 7, 2, 0, 48, 30, 8, 0, 7, "", "scythe", "owl_dagger", "psycho_axe"),
        weapon("legendary_sausage", "Legendary Sausage", 2, WEAPON_MELEE, PATTERN_ORBIT, 900, 5, 2, 5, 40, 14, 4, 0, 7, "", "bullet", "bl_book", "sausage")
    };

    static final WeaponDef[] WEAPONS = WikiCatalogData.buildWeapons(BASE_WEAPONS);

    /**
     * Shop passives, one per row on the shop screen.
     *
     * <p>Audited against RunWorld rather than invented. Every effect below either drives a field the
     * run already computes, or fills one that existed but was never set:
     *
     * <ul>
     *   <li>{@code body} feeds {@code baseMaxHp}, the same source the level-up Max HP stat writes.</li>
     *   <li>{@code might} feeds {@code atkPercentBonus}, applied to outgoing damage.</li>
     *   <li>{@code swift} feeds {@code speedPercentBonus}.</li>
     *   <li>{@code guard} feeds {@code defensePercent}, which {@code damagePlayer} has always read
     *       but which nothing ever set: before this pass damage reduction was dead code.</li>
     *   <li>{@code fortune} feeds {@code coinDropBonus}, a new field on the enemy coin-drop roll.
     *       No item covers coin rate; this is the only coin-rate source in the game.</li>
     *   <li>{@code insight} feeds {@code expPercentBonus}, applied to EXP pickups.</li>
     *   <li>{@code precision} feeds {@code critPercentBonus}.</li>
     *   <li>{@code tempo} feeds {@code hastePercent}, which shortens weapon cooldowns.</li>
     * </ul>
     *
     * <p>Every row runs to {@link #PASSIVE_MAX_LEVEL} levels and every purchase applies to every
     * character, because a level bought once is not a per-character unlock.
     *
     * <p>Costs double per level and are capped, which keeps the arithmetic trivially bounded and
     * keeps the whole catalogue affordable: buying every row to 10/10 costs 5,795,500, so
     * the current starting capital of 9,999,999 covers a full clear-out with change left over. When
     * the coin economy is designed for real these curves are the first thing to revisit.
     *
     * <p>Order is the shop's display order and is load-bearing only for readability.
     */
    static final int PASSIVE_MAX_LEVEL = 10;

    static final PassiveDef[] PASSIVES = {
        passive("body",     "Vitality",   EFFECT_MAX_HP,       10, 2000, 2, 150000),
        passive("might",    "Might",      EFFECT_DAMAGE_PCT,    8, 2500, 2, 150000),
        passive("guard",    "Guard",      EFFECT_DEFENSE_PCT,   6, 3000, 2, 150000),
        passive("swift",    "Swift",      EFFECT_SPEED_PCT,    10, 2000, 2, 100000),
        passive("precision","Precision",  EFFECT_CRIT_PCT,      4, 3500, 2, 150000),
        passive("insight",  "Insight",    EFFECT_EXP_PCT,      12, 2500, 2, 100000),
        passive("fortune",  "Fortune",    EFFECT_COIN_PCT,     15, 4000, 2, 200000),
        passive("tempo",    "Tempo",      EFFECT_HASTE_PCT,     6, 3000, 2, 100000)
    };

    /**
     * Price of the next level of a passive, i.e. level {@code currentLevel + 1}.
     *
     * <p>Computed in {@code long} and clamped, so a deep level or a large base cost can neither
     * overflow into a negative price nor wrap into something affordable. Returns {@code -1} for an
     * unknown id, which callers treat as "not purchasable".
     */
    static long passiveCost(PassiveDef def, int currentLevel) {
        if (def == null || currentLevel < 0 || currentLevel >= def.maxLevel) return -1L;
        long cost = def.baseCost;
        for (int i = 0; i < currentLevel && cost < def.costCap; i++) cost *= def.costGrowth;
        if (cost > def.costCap) cost = def.costCap;
        return cost;
    }

    static PassiveDef passive(String id) {
        for (int i = 0; i < PASSIVES.length; i++) if (PASSIVES[i].id.equals(id)) return PASSIVES[i];
        return null;
    }

    static PassiveDef passive(int index) {
        if (index < 0 || index >= PASSIVES.length) return null;
        return PASSIVES[index];
    }

    static PassiveDef passive(String id, String name, int effect, int value,
            int baseCost, int costGrowth, int costCap) {
        PassiveDef d = new PassiveDef();
        d.id = id; d.name = name; d.effect = effect; d.value = value;
        d.maxLevel = PASSIVE_MAX_LEVEL;
        d.baseCost = baseCost; d.costGrowth = costGrowth; d.costCap = costCap;
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
        enemy("biji_sawit", "Biji Sawit", AI_ORBIT_CHARGER, 35, 3, 0.48, 800, 10, 80, 18, 16, "BijiSawit", false)
    };

    static final BossDef[] BOSSES = {
        // Stage 1 Hard stats verified against the wiki Stage 1 (Hard) enemy
        // composition (HP/ATK/SPD/EXP columns); SPD has no per-boss runtime
        // field (boss.speed is derived), so only HP/ATK/EXP are applied here.
        boss("fubuzilla", "Fubuzilla", 15000, 22, AI_SHOOTER, 1400, 1500, "fubuzilla", false),
        boss("smol_ame", "Smol Ame", 12000, 20, AI_CHASER, 1000, 1500, "AchanBoss", true),
        boss("mikodanye", "Mikodanye", 2200, 24, AI_SHOOTER, 1300, 65, "Mikodanye", false),
        boss("achan", "A-chan", 2000, 22, AI_CHARGER, 850, 60, "AchanBoss", false),
        boss("shubangelion", "Shubangelion", 3200, 28, AI_CHARGER, 1200, 85, "Shubangelion_walk", false),
        boss("spiderchama", "Spiderchama", 3600, 30, AI_SHOOTER, 1000, 90, "Pekodam", true),
        boss("udin", "Udin", 4200, 34, AI_SHOOTER, 1100, 110, "Udin_walk", false),
        boss("area15", "Area 15", 4600, 36, AI_BOMBER, 1200, 120, "Pekodam", true),
        boss("nanora", "Nanora", 5000, 38, AI_CHASER, 900, 130, "Moonafic2", true),
        boss("pekodam", "Pekodam", 6000, 42, AI_SHOOTER, 850, 160, "Pekodam", false),
        boss("halloween_bae", "Halloween Bae", 72000, 27, AI_BOMBER, 1000, 6000, "Pekodam", true),
        boss("nodoka", "Harusaki Nodoka", 5000, 40, AI_SHOOTER, 950, 140, "Mikodanye", true),
        boss("halloween_myth", "Halloween Myth", 6000, 44, AI_CHARGER, 1000, 160, "Pekodam", true),
        boss("goriela", "Goriela", 6500, 46, AI_SHOOTER, 900, 180, "Pekodam", true),
        // Original design: WoWo, guardian of the burnt palm grove. Keeps its distance
        // and fires a widening radial seed burst, so the fight is about dodging
        // rings rather than chasing a single bullet. Tuned above Pekodam, the final
        // boss of the stage this one unlocks behind, on both axes: it closes the
        // normal stage list and must not feel like a step back.
        boss("wowo", "WoWo", 9000, 44, AI_SHOOTER, 1000, 300, "WoWo", false)
    };

    static final ModeDef[] MODES = {
        mode("stage", "Stage Mode", COMPLETE_FINAL_BOSS, 0, false, true, false, "Defeat the stage's final boss"),
        mode("endless", "Endless Mode", COMPLETE_ON_DEATH, 0, true, true, false, "Survive until defeated"),
        mode("time", "Time Mode", COMPLETE_KILL_TARGET, 4000, false, false, true, "Defeat 4000 fans as quickly as possible")
    };

    static final StageDef[] STAGES = {
        stage("stage1", "Grassy Plains", false, false, 0, "grass_field", 0, "shrimp", "dark_shrimp", "shrimp", 1300, 20, 3000, -1, "fubuzilla", 600, "smol_ame", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; combat values approximated"),
        stage("stage2", "Holo Office", false, false, 1, "office", 1, "sukonbu", "kuro_sukonbu", "sukonbu", 1150, 24, 5000, 0, "mikodanye", 600, "achan", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; combat values approximated"),
        stage("stage3", "Halloween Castle", false, false, 2, "castle", 2, "matsurisu", "matsurisu3", "matsurisu", 1000, 28, 7500, 1, "shubangelion", 600, "spiderchama", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; Spiderchama asset approximated"),
        stage("stage4", "Gelora Bung Yagoo", false, false, 3, "carpet", 3, "moonafic", "moonafic_vip", "moonafic", 900, 32, 10000, 2, "udin", 600, "area15", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; Area 15 asset approximated"),
        // unlockRequirement 99 marks this stage always unlocked. Verified from the reference JAR
        // bytecode: the stage5 entry pushes interval 850, cap 36, coins 15000, then 99, then the
        // boss string "nanora". FullGameCanvas.stageUnlocked treats 99 exactly like -1 and returns
        // true without consulting the save, so Fantasy Island is never locked in the reference.
        stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, "nousagi_a", "nousagi_b", "nousagi_c", 850, 36, 15000, 99, "nanora", 600, "pekodam", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; Nanora asset approximated"),
        stage("stage1h", "Grassy Plains (Night)", true, false, 5, "grass", 5, "dark_shrimp", "shrimp", "dark_shrimp", 1000, 26, 5000, 1, "fubuzilla", 600, "halloween_bae", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m final boss uses fallback asset"),
        stage("stage2h", "Holo Office (Evening)", true, false, 6, "office", 6, "kuro_sukonbu", "sukonbu", "kuro_sukonbu", 900, 30, 15000, 5, "mikodanye", 600, "nodoka", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m Nodoka uses fallback asset"),
        stage("stage3h", "Halloween Castle (Myth)", true, false, 7, "castle", 7, "matsurisu3", "matsurisu", "matsurisu3", 850, 34, 30000, 6, "shubangelion", 600, "halloween_myth", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m Halloween Myth uses fallback asset"),
        stage("stage4h", "Gelora Bung Yagoo (Night)", true, false, 8, "yagoo", 8, "moonafic_vip", "moonafic", "moonafic_vip", 800, 38, 50000, 7, "udin", 600, "goriela", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m Goriela uses fallback asset"),
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
        stage("stage6", "Arena Hutan Sawit", false, false, 10, "palm_forest", 10, "biji_sawit", "biji_sawit", "biji_sawit", 1400, 18, 12000, 99, "wowo", 600, "wowo", 1200, "", "Original custom stage; art from tools/generate_palm_forest_sprites.py and tools/generate_palm_forest_map.py; combat values are original and unvalidated", 4, 4)
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
 * One permanent character upgrade sold by the shop.
 *
 * <p>Each passive is a single stat fed by one integer {@code value} per level, and every effect
 * below maps to a field RunWorld already computes, so a passive needs no new damage or pickup code
 * path. Levels are stored per character and read at the start of every run.
 */
    static final class PassiveDef {
        String id;
        String name;
        int effect;      // one of the EFFECT_* constants.
        int value;       // per level, in the same unit the effect is expressed in.
        int maxLevel;
        int baseCost;    // coin price of level 1.
        int costGrowth;  // each further level costs baseCost * growth, capped at costCap.
        int costCap;
    }

    static final CollabDef[] COLLABS;

    static {
        java.io.InputStream is = ContentCatalog.class.getResourceAsStream("/data/wiki/collabs.csv");
        CollabDef[] defs = null;
        if (is != null) {
            try {
                defs = WikiCatalogData.buildCollabDefs(is);
            } catch (IOException ie) {
                // Fallback if CSV parsing fails
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
        return character(id, name, hp, atk, spd, crit, size, weapon, special, skill, playable, group, false);
    }

    static CharacterDef character(String id, String name, int hp, int atk, int spd, int crit,
            int size, String weapon, String special, String skill, boolean playable, String group,
            boolean fallbackArt) {
        CharacterDef d = new CharacterDef();
        d.id = id; d.name = name; d.hp = hp; d.atkPercent = atk; d.spdPercent = spd;
        d.critPercent = crit; d.size = size; d.mainWeapon = weapon; d.special = special;
        d.skill = skill; d.playable = playable; d.assetGroup = group;
        d.fallbackArt = fallbackArt; d.gameplayApproximation = fallbackArt;
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
            int cooldown, int exp, String sprite, boolean approximation) {
        BossDef d = new BossDef();
        d.id = id; d.name = name; d.hp = hp; d.atk = atk; d.behavior = behavior;
        d.cooldownMs = cooldown; d.exp = exp; d.sprite = sprite;
        d.assetApproximation = approximation;
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
