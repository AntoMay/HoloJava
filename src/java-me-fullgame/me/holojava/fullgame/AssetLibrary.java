package me.holojava.fullgame;

import javax.microedition.lcdui.Image;

/** Loads a bounded, stage-scoped Java ME asset set from packaged JAR resources. */
final class AssetLibrary {
    static final class CharacterAssets {
        Image[] idle;
        Image[] run;
        Image portrait;
        Image weapon;
    }

    static final class AnimationSet {
        Image[] frames;
    }

    final CharacterAssets[] characters = new CharacterAssets[ContentCatalog.CHARACTERS.length];
    final AnimationSet[] enemies = new AnimationSet[ContentCatalog.ENEMIES.length];
    final AnimationSet[] bosses = new AnimationSet[ContentCatalog.BOSSES.length];
    Image[] expFrames;
    Image coin;
    Image bullet;
    Image ameBullet;
    Image axe;
    Image bomb;
    Image trident;
    Image scythe;
    Image phoenix;
    Image tentacle;
    Image pickupIcon;

    Image tileGrass;
    Image tileOffice;
    Image tileCastle;
    Image tileCarpet;
    Image tileYagoo;
    Image tileFantasy;
    Image tileConcert;
    Image tileBeach;

    private final String[] enemyDirs = { "Shrimp", "Shrimp2", "Sukonbu", "Sukonbu2", "Matsurisu",
            "Matsurisu3", "Moonafic", "Moonafic2", "NousagiA", "NousagiB",
            "NousagiC", "NousagiD", "Otaku" };
    private final String[] enemyPrefixes = { "spr_Shrimp_", "spr_Shrimp2_", "spr_Sukonbu_", "spr_Sukonbu2_",
            "spr_Matsurisu_", "spr_Matsurisu3_", "spr_Moonafic_", "spr_Moonafic2_",
            "spr_NousagiEnemyA_", "spr_NousagiEnemyB_", "spr_NousagiEnemyC_",
            "spr_NousagiEnemyD_", "spr_DebuffOtakuA_" };
    private final int[] enemyCounts = { 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4 };
    private final String[] bossDirs = { "fubuzilla", "AchanBoss", "Mikodanye", "AchanBoss",
            "Shubangelion_walk", "Pekodam", "Udin_walk", "Pekodam", "Moonafic",
            "Pekodam", "Pekodam", "Mikodanye", "Pekodam", "Pekodam" };
    private final String[] bossPrefixes = { "spr_fubuzilla_", "spr_AchanBoss_", "spr_Mikodanye_",
            "spr_AchanBoss_", "spr_Shubangelion_walk_", "spr_Pekodam_",
            "spr_Udin_walk_", "spr_Pekodam_", "spr_Moonafic_", "spr_Pekodam_",
            "spr_Pekodam_", "spr_Mikodanye_", "spr_Pekodam_", "spr_Pekodam_" };
    private final int[] bossCounts = { 12, 4, 8, 4, 8, 4, 6, 4, 3, 4, 4, 8, 4, 4 };

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
            a.portrait = null; // loaded lazily from the stable character-id portrait path.
            a.weapon = image("/assets/fullgame/projectiles/" + weaponFiles[i] + ".png");
        }

        expFrames = frames("/assets/fullgame/pickups/", 12, "spr_EXP_");
        coin = image("/assets/fullgame/pickups/coin.png");
        bullet = image("/assets/fullgame/projectiles/bullet.png");
        ameBullet = image("/assets/fullgame/projectiles/ame_bullet.png");
        axe = image("/assets/fullgame/projectiles/axe.png");
        bomb = image("/assets/fullgame/projectiles/bomb.png");
        trident = image("/assets/fullgame/projectiles/trident.png");
        scythe = image("/assets/fullgame/projectiles/scythe.png");
        phoenix = image("/assets/fullgame/projectiles/phoenix.png");
        tentacle = image("/assets/fullgame/projectiles/tentacle.png");
        pickupIcon = image("/assets/fullgame/ui/pickup_icon.png");
    }

    /** Releases stage-scoped enemy/boss images and loads only the selected stage's families. */
    void prepareStage(int stageIndex) {
        for (int i = 0; i < enemies.length; i++) enemies[i].frames = null;
        for (int i = 0; i < bosses.length; i++) bosses[i].frames = null;
        ContentCatalog.StageDef stage = ContentCatalog.stage(stageIndex);
        loadEnemy(stage.enemyA); loadEnemy(stage.enemyB); loadEnemy(stage.enemyC);
        if (!stage.timeStage) {
            loadBoss(stage.bossA); loadBoss(stage.bossB);
            if (stage.hard) loadBoss(thirdBossFor(stageIndex));
        }
    }

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
    }

    private void loadBoss(String id) {
        int index = bossIndex(id);
        if (index < 0 || bosses[index].frames != null) return;
        bosses[index].frames = frames("/assets/fullgame/bosses/" + bossDirs[index] + "/", bossCounts[index], bossPrefixes[index]);
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
            current.portrait = image("/assets/fullgame/portraits/" + ContentCatalog.CHARACTERS[index].id + ".png");
        }
        return current.portrait;
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

    Image[] bossFrames(int index) {
        if (bosses[index].frames == null) loadBoss(ContentCatalog.BOSSES[index].id);
        return bosses[index].frames;
    }

    Image tile(String key) {
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
}
