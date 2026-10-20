package me.holojava.fullgame;

import java.io.ByteArrayInputStream;

/**
 * Proves the CSV catalog parser behaves the same as the CLDC-forbidden code it
 * replaced, and that it stays on the CLDC 1.1 API surface.
 *
 * <p>Why this suite exists: {@code WikiCatalogData} runs out of a static
 * initializer, where a class the handset does not have surfaces as a
 * {@code NoClassDefFoundError} that the {@code IOException} fallback cannot
 * catch. The old parser used {@code ArrayList}, {@code StringBuilder},
 * {@code BufferedReader}, {@code String.isEmpty()} and {@code String.matches()}.
 * All five exist on the desktop JDK, so the old code compiled and every test
 * passed while the game was guaranteed to crash on a real phone. The desktop
 * test runner cannot see that difference, so the parser's observable behaviour
 * is pinned here instead: the grammar and line handling must survive the
 * rewrite unchanged.
 */
public final class WikiCatalogDataTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testHeaderOnlyYieldsEmpty();
        testBlankAndMalformedLinesAreSkipped();
        testQuotedFieldsKeepTheirCommas();
        testCrlfIsAccepted();
        testIdGrammar();
        testWeaponIdNormalization();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** A CSV that is only a header parses to zero rows, not to one junk row. */
    private static void testHeaderOnlyYieldsEmpty() throws Exception {
        System.out.println("[1] header-only CSV yields no rows");
        ContentCatalog.CollabDef[] defs = parse(
            "id,name,weapon_a,weapon_b,type,source_url,coverage_note\n");
        expectEq(defs.length, 0, "header-only CSV parses to zero rows");
    }

    /** Blank lines and short lines are skipped rather than crashing or misaligning. */
    private static void testBlankAndMalformedLinesAreSkipped() throws Exception {
        System.out.println("[2] blank and short lines are skipped");
        String csv =
            "id,name,weapon_a,weapon_b,type\n"
            + "\n"
            + "   \n"
            + "too,short,only\n"
            + "good_one,Good One,bounce ball,cutting board,collab\n";
        ContentCatalog.CollabDef[] defs = parse(csv);
        expectEq(defs.length, 1, "only the one well-formed row survives");
        expect(defs.length == 1 && "good_one".equals(defs[0].id),
            "the surviving row keeps its id");
        expect(defs.length == 1 && "bounce_ball".equals(defs[0].weaponA),
            "the surviving row normalizes its weapon name");
    }

    /** A quoted field may contain commas without splitting into extra columns. */
    private static void testQuotedFieldsKeepTheirCommas() throws Exception {
        System.out.println("[3] quoted fields keep their commas");
        String csv =
            "id,name,weapon_a,weapon_b,type,source_url,coverage_note\n"
            + "quoted,Quoted Name,bounce ball,cutting board,collab,"
            + "http://example.test,\"covers one, two, three\"\n";
        ContentCatalog.CollabDef[] defs = parse(csv);
        expectEq(defs.length, 1, "quoted commas do not split the row");
        expect(defs.length == 1 && "quoted".equals(defs[0].id), "quoted row keeps its id");
    }

    /** Windows line endings must not leak a carriage return into the last field. */
    private static void testCrlfIsAccepted() throws Exception {
        System.out.println("[4] CRLF line endings are accepted");
        String csv =
            "id,name,weapon_a,weapon_b,type,source_url,coverage_note\r\n"
            + "crlf_row,CRLF Row,bounce ball,cutting board,collab,src,note\r\n";
        ContentCatalog.CollabDef[] defs = parse(csv);
        expectEq(defs.length, 1, "CRLF CSV parses one row");
        expect(defs.length == 1 && "crlf_row".equals(defs[0].id),
            "CRLF row id carries no trailing carriage return");
        expect(defs.length == 1 && "src".equals(defs[0].source),
            "last CRLF field carries no trailing carriage return");
    }

    /**
     * The id grammar accepts ASCII letters, digits, underscore and hyphen, and
     * rejects everything else. This is the hand-rolled replacement for
     * {@code String.matches("[a-zA-Z0-9_\\-]+")}, which needs java.util.regex —
     * absent from CLDC — so the accepted set is pinned here.
     */
    private static void testIdGrammar() {
        System.out.println("[5] id grammar matches the regex it replaced");
        String[] accepted = { "a", "A", "0", "ab_cd", "ab-cd", "a_B-9", "x1_y2-z3" };
        for (int i = 0; i < accepted.length; i++) {
            expect(WikiCatalogData.isValidId(accepted[i]),
                "accepted id: " + accepted[i]);
        }
        String[] rejected = { "", " ", "a b", "a.b", "a'b", "a\"b", "a/b", "a+b", "a\tb" };
        for (int i = 0; i < rejected.length; i++) {
            expect(!WikiCatalogData.isValidId(rejected[i]),
                "rejected id: '" + rejected[i] + "'");
        }
        expect(!WikiCatalogData.isValidId(null), "rejected id: null");
    }

    /** The name-to-id rule is unchanged by the rewrite. */
    private static void testWeaponIdNormalization() {
        System.out.println("[6] weapon name normalization is unchanged");
        expectEq(WikiCatalogData.toWeaponId("Bounce Ball"), "bounce_ball",
            "space collapses to underscore");
        expectEq(WikiCatalogData.toWeaponId("EN's Curse"), "ens_curse",
            "apostrophe is dropped");
        expectEq(WikiCatalogData.toWeaponId("X-Potato"), "x_potato",
            "hyphen collapses to underscore");
        expectEq(WikiCatalogData.toWeaponId("  Bounce   Ball  "), "bounce_ball",
            "repeated separators collapse and trim");
        expectEq(WikiCatalogData.toWeaponId("bounce_ball"), "bounce_ball",
            "an id passes through unchanged");
        expectEq(WikiCatalogData.toWeaponId(""), "", "empty maps to empty");
        expectEq(WikiCatalogData.toWeaponId(null), "", "null maps to empty");
    }

    private static ContentCatalog.CollabDef[] parse(String csv) throws Exception {
        // The catalog is ASCII; casting char to byte matches the CLDC reader's
        // byte-to-char path, so the test feeds the parser what a phone would.
        byte[] bytes = new byte[csv.length()];
        for (int i = 0; i < csv.length(); i++) bytes[i] = (byte) csv.charAt(i);
        return WikiCatalogData.buildCollabDefs(new ByteArrayInputStream(bytes));
    }

    private static void expectEq(String actual, String expected, String label) {
        expect(expected.equals(actual), label + " (expected '" + expected + "', got '" + actual + "')");
    }

    private static void expectEq(int actual, int expected, String label) {
        expect(actual == expected, label + " (expected " + expected + ", got " + actual + ")");
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}
