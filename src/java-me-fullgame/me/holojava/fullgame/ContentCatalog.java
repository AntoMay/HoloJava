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
        item("headphones", "Headphones", 2, EFFECT_NEGATE, 15, 5, 4, "bullet"),
        item("uber_sheep", "Uber Sheep", 0, EFFECT_FOOD_DROP, 10, 5, 4, "bullet")
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
        enemy("otaku", "Otaku", AI_HEALER, 90, 6, 0.34, 1400, 20, 60, 22, 28, "Otaku", false)
    };

    static final BossDef[] BOSSES = {
        boss("fubuzilla", "Fubuzilla", 1200, 16, AI_SHOOTER, 1400, 40, "fubuzilla", false),
        boss("smol_ame", "Smol Ame", 1800, 20, AI_CHASER, 1000, 55, "AchanBoss", true),
        boss("mikodanye", "Mikodanye", 2200, 24, AI_SHOOTER, 1300, 65, "Mikodanye", false),
        boss("achan", "A-chan", 2000, 22, AI_CHARGER, 850, 60, "AchanBoss", false),
        boss("shubangelion", "Shubangelion", 3200, 28, AI_CHARGER, 1200, 85, "Shubangelion_walk", false),
        boss("spiderchama", "Spiderchama", 3600, 30, AI_SHOOTER, 1000, 90, "Pekodam", true),
        boss("udin", "Udin", 4200, 34, AI_SHOOTER, 1100, 110, "Udin_walk", false),
        boss("area15", "Area 15", 4600, 36, AI_BOMBER, 1200, 120, "Pekodam", true),
        boss("nanora", "Nanora", 5000, 38, AI_CHASER, 900, 130, "Moonafic2", true),
        boss("pekodam", "Pekodam", 6000, 42, AI_SHOOTER, 850, 160, "Pekodam", false),
        boss("halloween_bae", "Halloween Bae", 5200, 38, AI_BOMBER, 1000, 140, "Pekodam", true),
        boss("nodoka", "Harusaki Nodoka", 5000, 40, AI_SHOOTER, 950, 140, "Mikodanye", true),
        boss("halloween_myth", "Halloween Myth", 6000, 44, AI_CHARGER, 1000, 160, "Pekodam", true),
        boss("goriela", "Goriela", 6500, 46, AI_SHOOTER, 900, 180, "Pekodam", true)
    };

    static final ModeDef[] MODES = {
        mode("stage", "Stage Mode", COMPLETE_FINAL_BOSS, 0, false, true, false, "Defeat the stage's final boss"),
        mode("endless", "Endless Mode", COMPLETE_ON_DEATH, 0, true, true, false, "Survive until defeated"),
        mode("time", "Time Mode", COMPLETE_KILL_TARGET, 4000, false, false, true, "Defeat 4000 fans as quickly as possible")
    };

    static final StageDef[] STAGES = {
        stage("stage1", "Grassy Plains", false, false, 0, "grass", 0, "shrimp", "dark_shrimp", "shrimp", 1300, 20, 3000, -1, "fubuzilla", 600, "smol_ame", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; combat values approximated"),
        stage("stage2", "Holo Office", false, false, 1, "office", 1, "sukonbu", "kuro_sukonbu", "sukonbu", 1150, 24, 5000, 0, "mikodanye", 600, "achan", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; combat values approximated"),
        stage("stage3", "Halloween Castle", false, false, 2, "castle", 2, "matsurisu", "matsurisu3", "matsurisu", 1000, 28, 7500, 1, "shubangelion", 600, "spiderchama", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; Spiderchama asset approximated"),
        stage("stage4", "Gelora Bung Yagoo", false, false, 3, "carpet", 3, "moonafic", "moonafic_vip", "moonafic", 900, 32, 10000, 2, "udin", 600, "area15", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; Area 15 asset approximated"),
        stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, "nousagi_a", "nousagi_b", "nousagi_c", 850, 36, 15000, 3, "nanora", 600, "pekodam", 1200, "https://holocure.wiki.gg/wiki/Stage", "documented stage/boss timing; Nanora asset approximated"),
        stage("stage1h", "Grassy Plains (Night)", true, false, 5, "grass", 5, "dark_shrimp", "shrimp", "dark_shrimp", 1000, 26, 5000, 1, "fubuzilla", 600, "halloween_bae", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m final boss uses fallback asset"),
        stage("stage2h", "Holo Office (Evening)", true, false, 6, "office", 6, "kuro_sukonbu", "sukonbu", "kuro_sukonbu", 900, 30, 15000, 5, "mikodanye", 600, "nodoka", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m Nodoka uses fallback asset"),
        stage("stage3h", "Halloween Castle (Myth)", true, false, 7, "castle", 7, "matsurisu3", "matsurisu", "matsurisu3", 850, 34, 30000, 6, "shubangelion", 600, "halloween_myth", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m Halloween Myth uses fallback asset"),
        stage("stage4h", "Gelora Bung Yagoo (Night)", true, false, 8, "yagoo", 8, "moonafic_vip", "moonafic", "moonafic_vip", 800, 38, 50000, 7, "udin", 600, "goriela", 1200, "https://holocure.wiki.gg/wiki/Stage", "Hard variant documented; 20m Goriela uses fallback asset"),
        stage("time1", "Concert Stage", false, true, 9, "concert", 9, "otaku", "otaku", "otaku", 650, 40, 0, 2, "", 0, "", 0, "https://holocure.wiki.gg/wiki/Stage", "Time Mode target 4000; collabs and super items disabled")
    };

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
        StageDef d = new StageDef();
        d.id = id; d.name = name; d.hard = hard; d.timeStage = time; d.order = order;
        d.tile = tile; d.palette = palette; d.enemyA = enemyA; d.enemyB = enemyB;
        d.enemyC = enemyC; d.spawnIntervalMs = interval; d.maxEnemies = cap;
        d.coinClear = coins; d.unlockRequirement = unlock; d.bossA = bossA;
        d.bossATimeSec = timeA; d.bossB = bossB; d.bossBTimeSec = timeB;
        d.documentedSource = source; d.coverage = coverage;
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
