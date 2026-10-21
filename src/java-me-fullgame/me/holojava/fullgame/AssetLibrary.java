package me.holojava.fullgame;

import javax.microedition.lcdui.Image;

/** Loads a bounded, stage-scoped Java ME asset set from packaged JAR resources. */
final class AssetLibrary {
    static final class CharacterAssets {
        Image[] idle;
        Image[] run;
        Image[] idleM;
        Image[] runM;
        Image portrait;
        Image weapon;
    }

    static final class AnimationSet {
        Image[] frames;
        Image[] framesM;
    }

    static final class MeleeAnimation {
        Image[] frames;
        Image[] framesM;
        int frameDuration; // ms per frame
    }

    final CharacterAssets[] characters = new CharacterAssets[ContentCatalog.CHARACTERS.length];
    final AnimationSet[] enemies = new AnimationSet[ContentCatalog.ENEMIES.length];
    final AnimationSet[] bosses = new AnimationSet[ContentCatalog.BOSSES.length];
    final MeleeAnimation[] meleeAnims = new MeleeAnimation[ContentCatalog.CHARACTERS.length];
    Image[] expFrames;
    Image coin;
    Image food;
    Image bullet;
    Image ameBullet;
    Image axe;
    Image bomb;
    Image trident;
    Image scythe;
    Image phoenix;
    Image tentacle;
    Image pickupIcon;
    private Image logo;
    private Image menuLogo;
    private Image titleLogo;
    private Image sparkle0;
    private Image sparkle1;
    private Image titleBg;
    private Image splashBg;
    private Image menuLogoShine;
    private Image[] titleChars;        // 47 roster sprites, pre-scaled, display order
    private Image menuLabel;
    private Image[] icons;
    private Image[] texts;

    // HUD assets (new)
    private Image[] expbarAnim;          // 90-frame animated EXP bar
    private Image expbarBg;              // EXP bar background
    private Image expbarFrame;           // EXP bar frame
    private Image expbarShine;           // EXP bar shine overlay
    private Image spbarBg;               // SP bar background
    private Image spbarFill;             // SP bar fill (normal)
    private Image spbarFillRainbow;      // SP bar fill (rainbow/ready)
    private Image spbarFrame;            // SP bar frame
    private Image[] digitsWhite;         // 10 white digits
    private Image[] digitsYellow;        // 10 yellow digits
    private Image[] digitsPink;          // 10 pink digits
    private Image hpGreen;               // HP bar green (normal)
    private Image hpGreen1;              // HP bar green (alt)
    private Image hpPurple;              // HP bar purple (low HP)
    private Image hpIcon;                // HP icon
    private Image[] weaponCaseLevels;    // 5 weapon case level icons
    private Image[] mainWeaponCaseLevels; // 5 main weapon case level icons
    private Image[] keybindIcons;       // 16 keybind icons (hud_keybindIcons_*)
    private Image[] controllerIcons;    // 10 controller icons (hud_controllerButtonIcons_*)
    private Image dialogueBox;          // hud_dialogueBox_0.png
    private Image uiDialogueBox;        // ui_dialogueBox_0.png
    private Image shopCategory0;
    private Image shopCategory1;
    private Image shopCategory2;
    private Image shopButton0;
    private Image shopButton1;
    private Image shopButton2;
    private Image shopButton3;
    private Image upgradeWindow;
    private Image upgradeWindowSel0;
    private Image upgradeWindowSel1;
    private Image menuFirst0;
    private Image menuCharSelBG;
    private Image pauseMenu;
    private Image optionsMenu;

    Image tileGrass;
    Image tileGrassField;
    Image tileOffice;
    Image tileCastle;
    Image tileCarpet;
    Image tileYagoo;
    Image tileFantasy;
    Image tileConcert;
    Image tileBeach;
    Image tilePalmForest;

    /**
     * Composed background grid for the selected stage, or null when that stage repeats a single
     * floor tile instead. Held per stage rather than globally so switching stages releases the
     * previous arena's parts, the same discipline {@code prepareStage} applies to enemies and bosses.
     */
    private Image[][] mapParts;

    private final String[] enemyDirs = { "Shrimp", "Shrimp2", "Sukonbu", "Sukonbu2", "Matsurisu",
            "Matsurisu3", "Moonafic", "Moonafic2", "NousagiA", "NousagiB",
            "NousagiC", "NousagiD", "Otaku", "BijiSawit", "Kucing19" };
    private final String[] enemyPrefixes = { "spr_Shrimp_", "spr_Shrimp2_", "spr_Sukonbu_", "spr_Sukonbu2_",
            "spr_Matsurisu_", "spr_Matsurisu3_", "spr_Moonafic_", "spr_Moonafic2_",
            "spr_NousagiEnemyA_", "spr_NousagiEnemyB_", "spr_NousagiEnemyC_",
            "spr_NousagiEnemyD_", "spr_DebuffOtakuA_", "spr_BijiSawit_", "spr_Kucing19_" };
    private final int[] enemyCounts = { 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3 };
    // Parallel to ContentCatalog.BOSSES, index for index: each *_hard row shares its base
    // boss's art directory, prefix and frame count.
    private final String[] bossDirs = { "fubuzilla", "fubuzilla", "SmolAme", "SmolAme",
            "Mikodanye", "Mikodanye", "AchanBoss",
            "Shubangelion_walk", "Shubangelion_walk", "EldrichHaachama",
            "Udin_walk", "Udin_walk", "Moontato", "Nanora",
            "Pekodam", "BaeBoss", "NodokaBoss", "NurseCalli", "Goriela", "WoWo", "FufuFafa" };
    private final String[] bossPrefixes = { "spr_fubuzilla_", "spr_fubuzilla_",
            "spr_SmollAme_walk_", "spr_SmollAme_walk_", "spr_Mikodanye_", "spr_Mikodanye_",
            "spr_AchanBoss_", "spr_Shubangelion_walk_", "spr_Shubangelion_walk_",
            "spr_EldrichHaachama_walk_", "spr_Udin_walk_", "spr_Udin_walk_",
            "spr_Moontato_", "spr_Moonafic2_", "spr_Pekodam_",
            "spr_Bae3D_walk_", "spr_NodokaBoss_", "spr_NurseCalli_", "spr_Goriela_idle_",
            "spr_WoWo_", "spr_FufuFafa_" };
    private final int[] bossCounts = { 12, 12, 8, 8, 8, 8, 4, 8, 8, 8, 6, 6, 4, 3, 4, 8, 4, 6, 4, 4, 4 };

    private int failures;
    private String firstFailure = "";

    AssetLibrary() {
        for (int i = 0; i < ContentCatalog.ENEMIES.length; i++) enemies[i] = new AnimationSet();
        for (int i = 0; i < ContentCatalog.BOSSES.length; i++) bosses[i] = new AnimationSet();

        for (int i = 0; i < characters.length; i++) characters[i] = new CharacterAssets();
        String[] groups = { "Gura", "Ame", "Calli", "Kiara", "Ina" };
        int[] idleCounts = { 3, 3, 4, 4, 4 };
        String[] weaponFiles = { "trident", "ame_bullet", "scythe", "phoenix", "tentacle" };
        for (int i = 0; i < groups.length; i++) {
            CharacterAssets a = characters[i];
            String spritePrefix = "spr_" + groups[i] + "_";
            String idleName = "Gura".equals(groups[i]) ? "Idle" : "idle";
            String runName = "Gura".equals(groups[i]) ? "Run" : "run";
            a.idle = frames("/assets/fullgame/characters/" + groups[i] + "/idle/", idleCounts[i], spritePrefix + idleName + "_");
            a.run = frames("/assets/fullgame/characters/" + groups[i] + "/run/", 6, spritePrefix + runName + "_");
            a.idleM = mirrorAll(a.idle);
            a.runM = mirrorAll(a.run);
            a.portrait = null; // loaded lazily from the stable character-id portrait path.
            a.weapon = image("/assets/fullgame/projectiles/" + weaponFiles[i] + ".png");
        }

        expFrames = frames("/assets/fullgame/pickups/", 12, "spr_EXP_");
        coin = image("/assets/fullgame/pickups/coin.png");
        food = image("/assets/fullgame/pickups/spr_OkayuOnigiri_0.png");
        bullet = image("/assets/fullgame/projectiles/bullet.png");
        ameBullet = image("/assets/fullgame/projectiles/ame_bullet.png");
        axe = image("/assets/fullgame/projectiles/axe.png");
        bomb = image("/assets/fullgame/projectiles/bomb.png");
        trident = image("/assets/fullgame/projectiles/trident.png");
        scythe = image("/assets/fullgame/projectiles/scythe.png");
        phoenix = image("/assets/fullgame/projectiles/phoenix.png");
        tentacle = image("/assets/fullgame/projectiles/tentacle.png");
        pickupIcon = image("/assets/fullgame/ui/pickup_icon.png");

        // Melee animations loaded lazily on first use to avoid startup memory pressure
    }

    private MeleeAnimation loadMeleeAnim(String group, int frameCount, int frameDuration) {
        MeleeAnimation anim = new MeleeAnimation();
        anim.frames = new Image[frameCount];
        anim.frameDuration = frameDuration;
        String dir = "/assets/fullgame/melee/" + group + "/";
        for (int i = 0; i < frameCount; i++) {
            anim.frames[i] = image(dir + i + ".png");
        }
        return anim;
    }

    private MeleeAnimation loadGuraMelee() {
        MeleeAnimation anim = new MeleeAnimation();
        anim.frames = new Image[11];
        anim.frameDuration = 14;
        for (int i = 0; i < 11; i++) {
            anim.frames[i] = downscale(image("/assets/fullgame/melee/Gura/spr_GuraTridentThrust_" + i + ".png"));
        }
        anim.framesM = mirrorAll(anim.frames);
        return anim;
    }

    private MeleeAnimation loadAmeMelee() {
        MeleeAnimation anim = new MeleeAnimation();
        anim.frames = new Image[2];
        anim.frameDuration = 80;
        for (int i = 0; i < 2; i++) {
            anim.frames[i] = image("/assets/fullgame/melee/Ame/spr_Ame_bullet_" + i + ".png");
        }
        return anim;
    }

    private MeleeAnimation loadCalliMelee() {
        MeleeAnimation anim = new MeleeAnimation();
        anim.frames = new Image[20];
        anim.frameDuration = 8;
        for (int i = 0; i < 10; i++) {
            anim.frames[i] = downscale(image("/assets/fullgame/melee/Calli/spr_CalliSlash1_" + i + ".png"));
        }
        for (int i = 0; i < 10; i++) {
            anim.frames[10 + i] = downscale(image("/assets/fullgame/melee/Calli/spr_CalliSlash2_" + i + ".png"));
        }
        anim.framesM = mirrorAll(anim.frames);
        return anim;
    }

    private MeleeAnimation loadKiaraMelee() {
        MeleeAnimation anim = new MeleeAnimation();
        anim.frames = new Image[22];
        anim.frameDuration = 7;
        for (int i = 0; i < 12; i++) {
            anim.frames[i] = downscale(image("/assets/fullgame/melee/Kiara/spr_KiaraFireSlash_" + i + ".png"));
        }
        for (int i = 0; i < 10; i++) {
            anim.frames[12 + i] = downscale(image("/assets/fullgame/melee/Kiara/spr_KiaraSlash_" + i + ".png"));
        }
        anim.framesM = mirrorAll(anim.frames);
        return anim;
    }

    private MeleeAnimation loadInaMelee() {
        MeleeAnimation anim = new MeleeAnimation();
        anim.frames = new Image[13];
        anim.frameDuration = 12;
        for (int i = 0; i < 12; i++) {
            anim.frames[i] = downscale(image("/assets/fullgame/melee/Ina/spr_InaAttackFX_" + i + ".png"));
        }
        anim.frames[12] = downscale(image("/assets/fullgame/melee/Ina/spr_InaWeapon_0.png"));
        anim.framesM = mirrorAll(anim.frames);
        return anim;
    }

    /** Releases stage-scoped enemy/boss images and loads only the selected stage's families. */
    void prepareStage(int stageIndex) {
        for (int i = 0; i < enemies.length; i++) enemies[i].frames = null;
        for (int i = 0; i < bosses.length; i++) bosses[i].frames = null;
        mapParts = null;
        ContentCatalog.StageDef stage = ContentCatalog.stage(stageIndex);
        loadEnemy(stage.enemyA); loadEnemy(stage.enemyB); loadEnemy(stage.enemyC);
        if (!stage.timeStage) {
            loadBoss(stage.bossA); loadBoss(stage.bossB);
            if (stage.hard) loadBoss(thirdBossFor(stageIndex));
        }
        if (stage.mapCols > 0 && stage.mapRows > 0) loadMapParts(stage.tile, stage.mapCols, stage.mapRows);
    }

    /**
     * Loads a stage's composed background as a fixed COLSxROWS grid from
     * {@code /assets/fullgame/stages/<key>_parts/<key>_<row><col>.png}.
     *
     * <p>Row-major, both indices plain decimal digits, which is exactly what
     * {@code tools/generate_palm_forest_map.py} writes. A missing part yields a null cell rather
     * than throwing, so one bad file degrades to a hole in the arena instead of a dead screen.
     */
    private void loadMapParts(String key, int cols, int rows) {
        Image[][] grid = new Image[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                grid[row][col] = image("/assets/fullgame/stages/" + key + "_parts/"
                        + key + "_" + row + col + ".png");
            }
        }
        mapParts = grid;
    }

    /** The composed background grid for the selected stage, or null for a repeating-tile stage. */
    Image[][] stageMap() { return mapParts; }

    private String thirdBossFor(int stageIndex) {
        if (stageIndex == 5) return "halloween_bae";
        if (stageIndex == 6) return "nodoka";
        if (stageIndex == 7) return "halloween_myth";
        if (stageIndex == 8) return "goriela";
        return "pekodam";
    }

    private void loadEnemy(String id) {
        int index = enemyIndex(id);
        if (index < 0 || enemies[index].frames != null) return;
        enemies[index].frames = frames("/assets/fullgame/enemies/" + enemyDirs[index] + "/", enemyCounts[index], enemyPrefixes[index]);
        enemies[index].framesM = mirrorAll(enemies[index].frames);
    }

    private void loadBoss(String id) {
        int index = bossIndex(id);
        if (index < 0 || bosses[index].frames != null) return;
        bosses[index].frames = frames("/assets/fullgame/bosses/" + bossDirs[index] + "/", bossCounts[index], bossPrefixes[index]);
        bosses[index].framesM = mirrorAll(bosses[index].frames);
    }

    private int enemyIndex(String id) {
        for (int i = 0; i < ContentCatalog.ENEMIES.length; i++) if (ContentCatalog.ENEMIES[i].id.equals(id)) return i;
        return -1;
    }

    private int bossIndex(String id) {
        for (int i = 0; i < ContentCatalog.BOSSES.length; i++) if (ContentCatalog.BOSSES[i].id.equals(id)) return i;
        return -1;
    }

    /** Returns a character animation bundle, using an explicitly documented fallback family. */
    CharacterAssets character(int index) {
        if (index < 0 || index >= characters.length) index = 0;
        CharacterAssets current = characters[index];
        if (current.idle != null && current.run != null) return current;
        String group = ContentCatalog.CHARACTERS[index].assetGroup;
        int fallback = groupIndex(group);
        if (fallback < 0 || fallback >= characters.length) fallback = index % 5;
        CharacterAssets base = characters[fallback];
        current.idle = base.idle;
        current.run = base.run;
        current.weapon = base.weapon;
        return current;
    }

    Image characterPortrait(int index) {
        if (index < 0 || index >= characters.length) index = 0;
        CharacterAssets current = characters[index];
        if (current.portrait == null) {
            String id = ContentCatalog.CHARACTERS[index].id;
            current.portrait = image("/assets/fullgame/portraits/" + portraitFile(id) + ".png");
        }
        return current.portrait;
    }

    /** Returns a single idle animation frame for the character (for menu display). */
    Image characterIdle(int index, int frame) {
        if (index < 0 || index >= characters.length) index = 0;
        CharacterAssets current = characters[index];
        if (current.idle == null) character(index); // force load
        if (current.idle != null && current.idle.length > 0) {
            return current.idle[frame % current.idle.length];
        }
        return null;
    }

    /**
     * Resolves the portrait asset filename for a catalog id. Portrait files are named after the
     * documented wiki id, which is longer than the catalog id used by gameplay code, so the five
     * playable characters need an explicit mapping. Unmapped ids fall back to the catalog id.
     */
    private static String portraitFile(String catalogId) {
        if ("gura".equals(catalogId)) return "gawr_gura";
        if ("ame".equals(catalogId)) return "amelia_watson";
        if ("calli".equals(catalogId)) return "mori_calliope";
        if ("kiara".equals(catalogId)) return "takanashi_kiara";
        if ("ina".equals(catalogId)) return "ninomae_inanis";
        return catalogId;
    }

    private int groupIndex(String group) {
        if ("Gura".equals(group)) return 0;
        if ("Ame".equals(group)) return 1;
        if ("Calli".equals(group)) return 2;
        if ("Kiara".equals(group)) return 3;
        if ("Ina".equals(group)) return 4;
        return -1;
    }

    int failures() { return failures; }
    String firstFailure() { return firstFailure; }

    Image[] enemyFrames(int index) {
        if (enemies[index].frames == null) loadEnemy(ContentCatalog.ENEMIES[index].id);
        return enemies[index].frames;
    }

    Image[] enemyFramesM(int index) {
        if (enemies[index].frames == null) loadEnemy(ContentCatalog.ENEMIES[index].id);
        return enemies[index].framesM;
    }

    MeleeAnimation meleeAnim(int characterIndex) {
        if (characterIndex < 0 || characterIndex >= meleeAnims.length) return null;
        if (meleeAnims[characterIndex] == null) {
            switch (characterIndex) {
                case 0: meleeAnims[0] = loadGuraMelee(); break;  // Gura: 11 frames, ~14ms/frame
                case 1: meleeAnims[1] = loadAmeMelee(); break;   // Ame: 2 frames, 80ms/frame
                case 2: meleeAnims[2] = loadCalliMelee(); break; // Calli: 20 frames, 8ms/frame
                case 3: meleeAnims[3] = loadKiaraMelee(); break; // Kiara: 22 frames, ~7ms/frame
                case 4: meleeAnims[4] = loadInaMelee(); break;   // Ina: 13 frames, ~12ms/frame
            }
        }
        return meleeAnims[characterIndex];
    }

    Image[] bossFrames(int index) {
        if (bosses[index].frames == null) loadBoss(ContentCatalog.BOSSES[index].id);
        return bosses[index].frames;
    }

    Image[] bossFramesM(int index) {
        if (bosses[index].frames == null) loadBoss(ContentCatalog.BOSSES[index].id);
        return bosses[index].framesM;
    }

    Image tile(String key) {
        if ("grass_field".equals(key)) {
            if (tileGrassField == null) tileGrassField = image("/assets/fullgame/stages/grass_field.png");
            return tileGrassField;
        }
        if ("grass".equals(key)) {
            if (tileGrass == null) tileGrass = image("/assets/fullgame/stages/grass.png");
            return tileGrass;
        }
        if ("office".equals(key)) {
            if (tileOffice == null) tileOffice = image("/assets/fullgame/stages/office.png");
            return tileOffice;
        }
        if ("castle".equals(key)) {
            if (tileCastle == null) tileCastle = image("/assets/fullgame/stages/castle.png");
            return tileCastle;
        }
        if ("carpet".equals(key)) {
            if (tileCarpet == null) tileCarpet = image("/assets/fullgame/stages/carpet.png");
            return tileCarpet;
        }
        if ("yagoo".equals(key)) {
            if (tileYagoo == null) tileYagoo = image("/assets/fullgame/stages/yagoo.png");
            return tileYagoo;
        }
        if ("fantasy".equals(key)) {
            if (tileFantasy == null) tileFantasy = image("/assets/fullgame/stages/fantasy.png");
            return tileFantasy;
        }
        if ("concert".equals(key)) {
            if (tileConcert == null) tileConcert = image("/assets/fullgame/stages/concert.png");
            return tileConcert;
        }
        if ("palm_forest".equals(key)) {
            if (tilePalmForest == null) tilePalmForest = image("/assets/fullgame/stages/palm_forest.png");
            return tilePalmForest;
        }
        if (tileBeach == null) tileBeach = image("/assets/fullgame/stages/beach.png");
        return tileBeach;
    }

    Image projectile(String key) {
        if ("ame_bullet".equals(key)) return ameBullet;
        if ("axe".equals(key)) return axe;
        if ("bomb".equals(key)) return bomb;
        if ("trident".equals(key)) return trident;
        if ("scythe".equals(key)) return scythe;
        if ("phoenix".equals(key)) return phoenix;
        if ("tentacle".equals(key)) return tentacle;
        return bullet;
    }

    Image characterWeapon(int characterIndex) {
        return character(characterIndex).weapon;
    }

    /**
     * Menu identity art used by the splash screen and Main Menu. Both are loaded lazily and
     * cached, so the render loop never re-reads the files. Frames are pre-scaled to their display
     * size at asset-preparation time because CLDC 1.1 / MIDP 2.0 cannot scale images at runtime.
     */
    Image logo() {
        if (logo == null) logo = image("/assets/fullgame/ui/holocure_logo.png");
        return logo;
    }

    /**
     * The main menu logo — the smaller 176px-wide version of the HoloCure wordmark,
     * sourced from hud_titlebar_0.png (the actual title screen logo), not the splash logo.
     * Loaded lazily and cached.
     */
    Image titleLogo() {
        if (titleLogo == null) titleLogo = image("/assets/fullgame/ui/menu_logo.png");
        return titleLogo;
    }

    /**
     * Sparkle frame 0 for graduated members on the title screen (hudfx_sparkle_0, halved).
     */
    Image sparkle0() {
        if (sparkle0 == null) sparkle0 = image("/assets/fullgame/ui/sparkle_0.png");
        return sparkle0;
    }

    /**
     * Sparkle frame 1 for graduated members on the title screen (hudfx_sparkle_1, halved).
     */
    Image sparkle1() {
        if (sparkle1 == null) sparkle1 = image("/assets/fullgame/ui/sparkle_1.png");
        return sparkle1;
    }

    /**
     * Legacy menu logo (splash logo scaled down). Kept for compatibility but titleLogo()
     * should be used for the main menu title screen.
     */
    Image menuLogo() {
        if (menuLogo == null) menuLogo = image("/assets/fullgame/ui/holocure_logo_menu.png");
        return menuLogo;
    }

    /**
     * Title-screen backdrop (the rainbow burst with a baked dark icon bar) and the gold label
     * box behind the selected entry's name. Both are pre-scaled at asset-preparation time by
     * tools/prepare_title_menu_assets.py, which also bakes the roster sprites, logo, the eight
     * bar icons and the eight label texts below.
     */
    Image titleBg() {
        if (titleBg == null) titleBg = image("/assets/fullgame/ui/title_bg.png");
        return titleBg;
    }

    /**
     * Splash-screen backdrop: the same star stage as titleBg() but without the baked dark icon
     * bar, because the Splash carries no bar. tools/prepare_title_menu_assets.py emits both
     * variants from one base.
     */
    Image splashBg() {
        if (splashBg == null) splashBg = image("/assets/fullgame/ui/splash_bg.png");
        return splashBg;
    }

    /**
     * The second logo frame: the same wordmark with a bright diagonal shine swept across it.
     * drawMainMenu alternates it against titleLogo() every ~1.6s as a shine sweep.
     */
    Image menuLogoShine() {
        if (menuLogoShine == null) menuLogoShine = image("/assets/fullgame/ui/menu_logo_shine.png");
        return menuLogoShine;
    }

    /**
     * One roster sprite for the Main Menu crowd, indexed in display order (top row left-to-right
     * with the logo dropped in the top-row gap, then the lower rows). Group-loaded on first use
     * because every paint walks all 47, so one seek burst beats one per sprite. Each is pre-scaled
     * to its on-screen size by tools/prepare_title_menu_assets.py.
     */
    Image titleChar(int index) {
        if (titleChars == null) {
            titleChars = new Image[47];   // 47 roster members; CAST_* in FullGameCanvas
            for (int i = 0; i < titleChars.length; i++) {
                titleChars[i] = image("/assets/fullgame/ui/title_char_" + i + ".png");
            }
        }
        return titleChars[index];
    }

    Image menuLabel() {
        if (menuLabel == null) menuLabel = image("/assets/fullgame/ui/menu_label.png");
        return menuLabel;
    }

    /**
     * One frame of the title bar, indexed like FullGameCanvas's MAIN_* rows. Loaded as a group
     * on first use because every paint walks the whole bar; eight small icons cost one seek
     * burst instead of one per frame.
     */
    Image menuIcon(int index) {
        if (icons == null) {
            icons = new Image[8];   // eight bar entries; MAIN_ITEM_COUNT in FullGameCanvas
            for (int i = 0; i < icons.length; i++) {
                icons[i] = image("/assets/fullgame/ui/menu_icon_" + i + ".png");
            }
        }
        return icons[index];
    }

    /**
     * The entry name baked into the gold label box, one image per MAIN_* row, pre-rendered one
     * notch below MIDP's smallest font by tools/prepare_title_menu_assets.py. Group-loaded like
     * the icons; drawMainMenu falls back to a drawn string when an image is missing.
     */
    Image menuText(int index) {
        if (texts == null) {
            texts = new Image[8];   // eight entry names; MAIN_ITEMS in FullGameCanvas
            for (int i = 0; i < texts.length; i++) {
                texts[i] = image("/assets/fullgame/ui/menu_text_" + i + ".png");
            }
        }
        return texts[index];
    }

    // --- HUD asset loaders ---

    /**
     * 90-frame animated EXP bar (expbar_anim_00.png .. expbar_anim_89.png).
     * Pre-scaled to 76px wide (EXP_BAR_W) by prepare_hud_assets.py.
     */
    Image[] expbarAnim() {
        if (expbarAnim == null) {
            expbarAnim = new Image[90];
            for (int i = 0; i < 90; i++) {
                // CLDC-compatible: build path without String.format
                String path = "/assets/fullgame/ui/hud/expbar_anim/expbar_anim_";
                if (i < 10) path += "0";
                path += i + ".png";
                expbarAnim[i] = image(path);
            }
        }
        return expbarAnim;
    }

    /** EXP bar background (static). */
    Image expbarBg() {
        if (expbarBg == null) expbarBg = image("/assets/fullgame/ui/hud/expbar_bg.png");
        return expbarBg;
    }

    /** EXP bar frame overlay. */
    Image expbarFrame() {
        if (expbarFrame == null) expbarFrame = image("/assets/fullgame/ui/hud/expbar_frame.png");
        return expbarFrame;
    }

    /** EXP bar shine overlay (animated separately if desired). */
    Image expbarShine() {
        if (expbarShine == null) expbarShine = image("/assets/fullgame/ui/hud/expbar_shine.png");
        return expbarShine;
    }

    /** SP bar background. */
    Image spbarBg() {
        if (spbarBg == null) spbarBg = image("/assets/fullgame/ui/hud/spbar_bg.png");
        return spbarBg;
    }

    /** SP bar normal fill (gold). */
    Image spbarFill() {
        if (spbarFill == null) spbarFill = image("/assets/fullgame/ui/hud/spbar_fill.png");
        return spbarFill;
    }

    /** SP bar rainbow fill (when meter full/ready). */
    Image spbarFillRainbow() {
        if (spbarFillRainbow == null) spbarFillRainbow = image("/assets/fullgame/ui/hud/spbar_fill_rainbow.png");
        return spbarFillRainbow;
    }

    /** SP bar frame overlay. */
    Image spbarFrame() {
        if (spbarFrame == null) spbarFrame = image("/assets/fullgame/ui/hud/spbar_frame.png");
        return spbarFrame;
    }

    /** 10 white digit images (0-9). */
    Image[] digitsWhite() {
        if (digitsWhite == null) {
            digitsWhite = new Image[10];
            for (int i = 0; i < 10; i++) digitsWhite[i] = image("/assets/fullgame/ui/hud/digits_white/digit_" + i + ".png");
        }
        return digitsWhite;
    }

    /** 10 yellow digit images (0-9). */
    Image[] digitsYellow() {
        if (digitsYellow == null) {
            digitsYellow = new Image[10];
            for (int i = 0; i < 10; i++) digitsYellow[i] = image("/assets/fullgame/ui/hud/digits_yellow/digit_" + i + ".png");
        }
        return digitsYellow;
    }

    /** 10 pink digit images (0-9). */
    Image[] digitsPink() {
        if (digitsPink == null) {
            digitsPink = new Image[10];
            for (int i = 0; i < 10; i++) digitsPink[i] = image("/assets/fullgame/ui/hud/digits_pink/digit_" + i + ".png");
        }
        return digitsPink;
    }

    /** HP bar green (normal). */
    Image hpGreen() {
        if (hpGreen == null) hpGreen = image("/assets/fullgame/ui/hud/hp_green.png");
        return hpGreen;
    }

    /** HP bar green alt. */
    Image hpGreen1() {
        if (hpGreen1 == null) hpGreen1 = image("/assets/fullgame/ui/hud/hp_green_1.png");
        return hpGreen1;
    }

    /** HP bar purple (low HP). */
    Image hpPurple() {
        if (hpPurple == null) hpPurple = image("/assets/fullgame/ui/hud/hp_purple.png");
        return hpPurple;
    }

    /** HP icon. */
    Image hpIcon() {
        if (hpIcon == null) hpIcon = image("/assets/fullgame/ui/hud/hp_icon.png");
        return hpIcon;
    }

    /** Weapon case level icons (0-4). */
    Image[] weaponCaseLevels() {
        if (weaponCaseLevels == null) {
            weaponCaseLevels = new Image[5];
            for (int i = 0; i < 5; i++) weaponCaseLevels[i] = image("/assets/fullgame/ui/hud/weapon_case_level_" + i + ".png");
        }
        return weaponCaseLevels;
    }

    /** Main weapon case level icons (0-4). */
    Image[] mainWeaponCaseLevels() {
        if (mainWeaponCaseLevels == null) {
            mainWeaponCaseLevels = new Image[5];
            for (int i = 0; i < 5; i++) mainWeaponCaseLevels[i] = image("/assets/fullgame/ui/hud/mainweapon_case_level_" + i + ".png");
        }
        return mainWeaponCaseLevels;
    }

    /** 16 keybind icons (0-15) from hud_keybindIcons_*. */
    Image[] keybindIcons() {
        if (keybindIcons == null) {
            keybindIcons = new Image[16];
            for (int i = 0; i < 16; i++) keybindIcons[i] = image("/assets/fullgame/ui/hud/button_icons/keybind_" + i + ".png");
        }
        return keybindIcons;
    }

    /** 10 controller button icons (0-9) from hud_controllerButtonIcons_*. */
    Image[] controllerIcons() {
        if (controllerIcons == null) {
            controllerIcons = new Image[10];
            for (int i = 0; i < 10; i++) controllerIcons[i] = image("/assets/fullgame/ui/hud/button_icons/controller_" + i + ".png");
        }
        return controllerIcons;
    }

    /** hud_dialogueBox_0.png - main dialogue box background. */
    Image dialogueBox() {
        if (dialogueBox == null) dialogueBox = image("/assets/fullgame/ui/hud/hud_dialogueBox_0.png");
        return dialogueBox;
    }

    /** ui_dialogueBox_0.png - alternative dialogue box. */
    Image uiDialogueBox() {
        if (uiDialogueBox == null) uiDialogueBox = image("/assets/fullgame/ui/hud/ui_dialogueBox_0.png");
        return uiDialogueBox;
    }

    /** hud_shopCategories_0.png - shop category tab 0. */
    Image shopCategory0() {
        if (shopCategory0 == null) shopCategory0 = image("/assets/fullgame/ui/hud/shop_category_0.png");
        return shopCategory0;
    }

    /** hud_shopCategories_1.png - shop category tab 1. */
    Image shopCategory1() {
        if (shopCategory1 == null) shopCategory1 = image("/assets/fullgame/ui/hud/shop_category_1.png");
        return shopCategory1;
    }

    /** hud_shopCategories_2.png - shop category tab 2. */
    Image shopCategory2() {
        if (shopCategory2 == null) shopCategory2 = image("/assets/fullgame/ui/hud/shop_category_2.png");
        return shopCategory2;
    }

    /** hud_shopButtons_0.png - shop button 0. */
    Image shopButton0() {
        if (shopButton0 == null) shopButton0 = image("/assets/fullgame/ui/hud/shop_button_0.png");
        return shopButton0;
    }

    /** hud_shopButtons_1.png - shop button 1. */
    Image shopButton1() {
        if (shopButton1 == null) shopButton1 = image("/assets/fullgame/ui/hud/shop_button_1.png");
        return shopButton1;
    }

    /** hud_shopButtons_2.png - shop button 2. */
    Image shopButton2() {
        if (shopButton2 == null) shopButton2 = image("/assets/fullgame/ui/hud/shop_button_2.png");
        return shopButton2;
    }

    /** hud_shopButtons_3.png - shop button 3. */
    Image shopButton3() {
        if (shopButton3 == null) shopButton3 = image("/assets/fullgame/ui/hud/shop_button_3.png");
        return shopButton3;
    }

    /** ui_menu_upgrade_window_0.png - upgrade window background. */
    Image upgradeWindow() {
        if (upgradeWindow == null) upgradeWindow = image("/assets/fullgame/ui/hud/ui_menu_upgrade_window_0.png");
        return upgradeWindow;
    }

    /** ui_menu_upgrade_window_selected_0.png - upgrade window selected. */
    Image upgradeWindowSel0() {
        if (upgradeWindowSel0 == null) upgradeWindowSel0 = image("/assets/fullgame/ui/hud/ui_menu_upgrade_window_selected_0.png");
        return upgradeWindowSel0;
    }

    /** ui_menu_upgrade_window_selected_1.png - upgrade window selected alt. */
    Image upgradeWindowSel1() {
        if (upgradeWindowSel1 == null) upgradeWindowSel1 = image("/assets/fullgame/ui/hud/ui_menu_upgrade_window_selected_1.png");
        return upgradeWindowSel1;
    }

    /** menu_first_0.png - label box (same as menu_label but full). */
    Image menuFirst0() {
        if (menuFirst0 == null) menuFirst0 = image("/assets/fullgame/ui/hud/menu_first_0.png");
        return menuFirst0;
    }

    /** menu_charselecBG_0.png - character select background. */
    Image menuCharSelBG() {
        if (menuCharSelBG == null) menuCharSelBG = image("/assets/fullgame/ui/hud/menu_charselecBG_0.png");
        return menuCharSelBG;
    }

    /** hud_pausemenu_0.png - pause menu background. */
    Image pauseMenu() {
        if (pauseMenu == null) pauseMenu = image("/assets/fullgame/ui/hud/hud_pausemenu_0.png");
        return pauseMenu;
    }

    /** hud_optionsmenu_0.png - options menu background. */
    Image optionsMenu() {
        if (optionsMenu == null) optionsMenu = image("/assets/fullmenu/ui/hud/hud_optionsmenu_0.png");
        return optionsMenu;
    }

    private Image[] frames(String dir, int count) { return frames(dir, count, ""); }

    private Image[] frames(String dir, int count, String prefix) {
        Image[] result = new Image[count];
        for (int i = 0; i < count; i++) result[i] = image(dir + prefix + i + ".png");
        return result;
    }

    private Image image(String path) {
        try { return Image.createImage(path); }
        catch (Exception exception) {
            failures++;
            if (firstFailure.length() == 0) firstFailure = path;
            return null;
        }
    }

    /**
     * Horizontal mirror of raw ARGB rows. Pure integer math, unit-testable
     * headless. MIDP 2.0 has no image mirror primitive, so mirrored variants
     * are baked once at load; gameplay and frame counts are untouched.
     */
    static int[] mirrorPixels(int[] pixels, int w, int h) {
        int[] out = new int[pixels.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = pixels[y * w + (w - 1 - x)];
            }
        }
        return out;
    }

    /** Mirrored copy of one image; falls back to the source when unavailable. */
    private Image mirror(Image src) {
        if (src == null) return null;
        int sw = src.getWidth();
        int sh = src.getHeight();
        int[] pixels = new int[sw * sh];
        try {
            src.getRGB(pixels, 0, sw, 0, 0, sw, sh);
        } catch (Exception e) {
            return src;
        }
        try {
            return Image.createRGBImage(mirrorPixels(pixels, sw, sh), sw, sh, true);
        } catch (Exception e) {
            return src;
        }
    }

    /** Mirrored copy of a frame array; null entries stay null. */
    private Image[] mirrorAll(Image[] frames) {
        if (frames == null) return null;
        Image[] out = new Image[frames.length];
        for (int i = 0; i < frames.length; i++) out[i] = mirror(frames[i]);
        return out;
    }

    /**
     * Longest-side ceiling for melee effect frames, in pixels. Measured source
     * frames reach 141-182 px on the long side (player collision box is 24 px),
     * so 64 keeps effects readable at ~1.5x character width without dominating
     * the 320x240 playfield. Each shape keeps its own aspect ratio, hence its
     * own effective factor (0.35-0.55). Source assets are never modified.
     */
    static final int MELEE_EFFECT_CAP_PX = 64;

    /**
     * Target size preserving aspect ratio under the longest-side cap.
     * Pure integer math (CLDC-safe). Returns {width, height}; inputs at or
     * under the cap, and 1x1 empty markers, pass through unchanged.
     */
    static int[] fitWithin(int sw, int sh, int cap) {
        if (sw <= cap && sh <= cap) return new int[] { sw, sh };
        int longest = Math.max(sw, sh);
        int dw = Math.max(1, sw * cap / longest);
        int dh = Math.max(1, sh * cap / longest);
        return new int[] { dw, dh };
    }

    /**
     * Load-time nearest-neighbor downscale for melee effect frames only.
     * MIDP 2.0 has no runtime image scaling, so oversized frames are reduced
     * once at load; gameplay code, hitboxes, and frame counts are untouched.
     * Falls back to the source image when pixels are unavailable.
     */
    private Image downscale(Image src) {
        if (src == null) return null;
        int sw = src.getWidth();
        int sh = src.getHeight();
        int[] size = fitWithin(sw, sh, MELEE_EFFECT_CAP_PX);
        if (size[0] == sw && size[1] == sh) return src;
        int dw = size[0];
        int dh = size[1];
        int[] pixels = new int[sw * sh];
        try {
            src.getRGB(pixels, 0, sw, 0, 0, sw, sh);
        } catch (Exception e) {
            return src;
        }
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int sy = y * sh / dh;
            for (int x = 0; x < dw; x++) {
                out[y * dw + x] = pixels[sy * sw + x * sw / dw];
            }
        }
        try {
            return Image.createRGBImage(out, dw, dh, true);
        } catch (Exception e) {
            return src;
        }
    }
}
