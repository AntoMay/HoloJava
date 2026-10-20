package me.holojava.fullgame;

/**
 * Boss HP/ATK/EXP are the wiki's own figures, read from each stage page's
 * "Enemy Composition" table (https://holocure.wiki.gg/wiki/Stage_1_-_Grassy_Plains and
 * siblings, read 2026-10-20), and the catalog arranges them so progression reads
 * upward instead of jumping around.
 *
 * <p>The audit that prompted this file found a catalog where Mikodanye carried 2200 HP
 * (that was his EXP column) against A-chan's 45000 in the same stage, and Nodoka's 90000
 * was followed by Halloween Myth's 6000. This test pins every row so neither kind of
 * mix-up can return quietly, and asserts the shape the numbers now hold:
 *
 * <ul>
 *   <li>inside every stage, the 20:00 final outlasts the 10:00 opener;</li>
 *   <li>the normal track (orders 0-4 plus the custom stage6) climbs on both slots;</li>
 *   <li>each hard stage's final out-ranks the normal final below it, and the hard
 *       openers are non-decreasing. The hard finals are not asserted strictly
 *       monotonic, because they follow the wiki verbatim: Stage 3 (Hard) carries the
 *       five-member Halloween Myth at its group mean of 79600, under Nodoka's 90000.</li>
 * </ul>
 */
public final class BossStatsWikiTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        testEveryBossRowMatchesTheWiki();
        testEveryStageFinalOutlastsItsOpener();
        testNormalTrackClimbs();
        testHardTrackStepsUpFromNormal();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** The full table: one row per wiki appearance, originals unflagged. */
    private static void testEveryBossRowMatchesTheWiki() {
        System.out.println("[1] every boss row carries the wiki's figures");
        expectEq(ContentCatalog.BOSSES.length, 21, "the catalog holds every appearance once");

        // Stage 1: Fubuzilla 10:00, Smol Ame 20:00 (Normal); both at 10:00 (Hard).
        wiki("fubuzilla", 8000, 15, 2000, true);
        wiki("fubuzilla_hard", 15000, 22, 1500, true);
        wiki("smol_ame", 25000, 20, 5000, true);
        wiki("smol_ame_hard", 12000, 20, 1500, true);
        // Stage 2: Mikodanye 10:00, A-chan 20:00; Hard schedules Mikodanye 15000 at 10:00.
        wiki("mikodanye", 16000, 16, 2200, true);
        wiki("mikodanye_hard", 15000, 18, 2000, true);
        wiki("achan", 45000, 20, 6000, true);
        // Stage 3: Shubangelion 10:00, Spiderchama 20:00; Hard has Shubangelion 35000.
        wiki("shubangelion", 24000, 22, 2000, true);
        wiki("shubangelion_hard", 35000, 30, 2000, true);
        wiki("spiderchama", 55000, 25, 6000, true);
        // Stage 4: Udin 10:00; Area 15 is three sprites carried at their mean.
        wiki("udin", 32000, 22, 2000, true);
        wiki("udin_hard", 60000, 35, 2000, true);
        wiki("area15", 55000, 23, 2000, true);       // (53000+57000+55000)/3, (24+20+25)/3
        // Stage 5: Nanora 10:00, Pekodam 20:00.
        wiki("nanora", 50000, 27, 1400, true);
        wiki("pekodam", 100000, 35, 3000, true);
        // Hard-only finals.
        wiki("halloween_bae", 72000, 27, 6000, true);
        wiki("nodoka", 90000, 30, 6000, true);
        // Halloween Myth is five members carried at their mean:
        // (78000+83000+80000+82000+75000)/5, (35+28+28+33+30)/5=30.8->31, (2000*4+5000)/5.
        wiki("halloween_myth", 79600, 31, 2600, true);
        wiki("goriela", 180000, 47, 3000, true);
        // The two originals: no wiki figure exists, so documentedBaseStats stays false.
        wiki("wowo", 110000, 44, 3000, false);
        wiki("fufufafa", 125000, 48, 4000, false);
    }

    private static void wiki(String id, int hp, int atk, int exp, boolean documented) {
        ContentCatalog.BossDef def = ContentCatalog.boss(id);
        expect(def != null, id + " is defined");
        if (def == null) return;
        expectEq(def.hp, hp, id + " hp");
        expectEq(def.atk, atk, id + " atk");
        expectEq(def.exp, exp, id + " exp");
        expect(def.documentedBaseStats == documented,
            id + " documentedBaseStats is " + documented);
    }

    /** Inside a stage the fight closes on: the final has to outlast the opener. */
    private static void testEveryStageFinalOutlastsItsOpener() {
        System.out.println("[2] every stage's 20:00 boss outlasts its 10:00 boss");
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            ContentCatalog.StageDef s = ContentCatalog.STAGES[i];
            if (s.bossA == null || s.bossA.length() == 0) continue;   // the time stage schedules none
            ContentCatalog.BossDef a = ContentCatalog.boss(s.bossA);
            ContentCatalog.BossDef b = ContentCatalog.boss(s.bossB);
            expect(a != null && b != null, s.id + " boss rows resolve");
            if (a == null || b == null) continue;
            expect(a.hp < b.hp,
                s.id + ": " + a.id + " (" + a.hp + ") outlasted by " + b.id + " (" + b.hp + ")");
        }
        // Stage 1 Hard's second 10:00 boss reaches the slot through hardSecondBoss, not
        // the stage table, so it gets its own check against the same final.
        ContentCatalog.BossDef smolHard = ContentCatalog.boss("smol_ame_hard");
        ContentCatalog.BossDef bae = ContentCatalog.boss("halloween_bae");
        expect(smolHard != null && bae != null && smolHard.hp < bae.hp,
            "stage1h's second opener (smol_ame_hard) also stays under halloween_bae");
    }

    /** Normal stages in order, then the custom stage6 that closes the normal track. */
    private static void testNormalTrackClimbs() {
        System.out.println("[3] the normal track climbs on both slots");
        String[] openers = { "fubuzilla", "mikodanye", "shubangelion", "udin", "nanora", "wowo" };
        String[] finals = { "smol_ame", "achan", "spiderchama", "area15", "pekodam", "fufufafa" };
        for (int i = 1; i < openers.length; i++) {
            int prev = ContentCatalog.boss(openers[i - 1]).hp;
            int now = ContentCatalog.boss(openers[i]).hp;
            expect(now > prev,
                "opener " + openers[i] + " (" + now + ") > " + openers[i - 1] + " (" + prev + ")");
        }
        for (int i = 1; i < finals.length; i++) {
            int prev = ContentCatalog.boss(finals[i - 1]).hp;
            int now = ContentCatalog.boss(finals[i]).hp;
            expect(now >= prev,
                "final " + finals[i] + " (" + now + ") >= " + finals[i - 1] + " (" + prev + ")");
        }
        // WoWo/FufuFafa close the track: the stage6 contract says they out-rank Pekodam.
        expect(ContentCatalog.boss("wowo").hp > ContentCatalog.boss("pekodam").hp,
            "stage6's opener still outlasts the previous final");
    }

    /** Hard stages run beside the normal ones but must still step up from them. */
    private static void testHardTrackStepsUpFromNormal() {
        System.out.println("[4] every hard final out-ranks the normal final below it");
        String[][] finals = {
            { "halloween_bae", "smol_ame" },
            { "nodoka", "achan" },
            { "halloween_myth", "spiderchama" },
            { "goriela", "area15" },
        };
        for (int i = 0; i < finals.length; i++) {
            int hard = ContentCatalog.boss(finals[i][0]).hp;
            int normal = ContentCatalog.boss(finals[i][1]).hp;
            expect(hard >= normal,
                finals[i][0] + " (" + hard + ") >= " + finals[i][1] + " (" + normal + ")");
        }
        String[] hardOpeners = { "fubuzilla_hard", "mikodanye_hard", "shubangelion_hard", "udin_hard" };
        for (int i = 1; i < hardOpeners.length; i++) {
            int prev = ContentCatalog.boss(hardOpeners[i - 1]).hp;
            int now = ContentCatalog.boss(hardOpeners[i]).hp;
            expect(now >= prev,
                "hard opener " + hardOpeners[i] + " (" + now + ") >= " + hardOpeners[i - 1]
                    + " (" + prev + ")");
        }
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
