package me.holojava.fullgame;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Proves the collab catalog is packaged and live.
 *
 * <p>The path under test:
 * <ul>
 *   <li>{@code data/wiki/collabs.csv} exists in the built JAR at the exact
 *       resource path {@code ContentCatalog} reads
 *       ({@code /data/wiki/collabs.csv}).</li>
 *   <li>The JAR entry parses via {@code WikiCatalogData.buildCollabDefs} to
 *       the same row count the runtime catalog holds, proving the runtime
 *       read the CSV rather than the 5-row fallback.</li>
 *   <li>Normalized prerequisite ids of the implemented collabs resolve via
 *       {@code ContentCatalog.weapon}, proving display-name normalization.</li>
 * </ul>
 *
 * <p>A missing JAR or missing entry is an explicit packaging failure, not a
 * skip: the build must package the CSV for collab offers to work.
 */
public final class CollabCsvPackagedTest {

    private static int checks = 0;
    private static int failures = 0;

    // The development phone artifact; tools/build_fullgame.sh writes it.
    private static final String JAR_PATH =
        "dist/DevBuild/mobile/HoloJavaFullGame.jar";
    private static final String ENTRY = "data/wiki/collabs.csv";

    public static void main(String[] args) throws Exception {
        testResourceOnClasspath();
        testJarEntryMatchesRuntime();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** The staged/test classpath must expose the CSV at the runtime path. */
    private static void testResourceOnClasspath() throws Exception {
        System.out.println("[1] runtime resource path is readable");
        InputStream is = ContentCatalog.class.getResourceAsStream("/" + ENTRY);
        expect(is != null, "getResourceAsStream(/" + ENTRY + ") is non-null");
        if (is != null) {
            byte[] bytes = readAll(is);
            is.close();
            expect(bytes.length > 0, "classpath CSV entry is non-empty");
        }
    }

    /** The built JAR entry parses to exactly the runtime COLLABS content. */
    private static void testJarEntryMatchesRuntime() throws Exception {
        System.out.println("[2] built JAR entry matches the live catalog");
        File jar = new File(JAR_PATH);
        expect(jar.isFile(), "built JAR exists: " + JAR_PATH);
        if (!jar.isFile()) return;

        ZipFile zip = new ZipFile(jar);
        ZipEntry entry = zip.getEntry(ENTRY);
        expect(entry != null, "JAR contains " + ENTRY);
        if (entry == null) {
            zip.close();
            return;
        }
        InputStream is = zip.getInputStream(entry);
        byte[] bytes = readAll(is);
        is.close();
        zip.close();
        expect(bytes.length > 0, "JAR CSV entry is non-empty");

        InputStream parse = new java.io.ByteArrayInputStream(bytes);
        ContentCatalog.CollabDef[] defs = WikiCatalogData.buildCollabDefs(parse);
        parse.close();
        expect(defs.length > 5,
            "parsed JAR CSV has more than the 5-row fallback (got " + defs.length + ")");
        expect(defs.length == ContentCatalog.COLLABS.length,
            "runtime COLLABS (" + ContentCatalog.COLLABS.length
            + ") matches JAR CSV rows (" + defs.length + ")");
        for (int i = 0; i < defs.length; i++) {
            expect(defs[i].id.equals(ContentCatalog.COLLABS[i].id),
                "row " + i + " id matches runtime (" + defs[i].id + ")");
        }

        // The implemented collabs must have resolvable normalized prerequisites.
        String[][] known = {
            {"absolute_wall", "bounce_ball", "cutting_board"},
            {"bl_fujoshi", "bl_book", "psycho_axe"},
            {"black_plague", "ens_curse", "owl_dagger"},
            {"blood_lust", "owl_dagger", "psycho_axe"},
            {"legendary_sausage", "bl_book", "sausage"},
        };
        for (int k = 0; k < known.length; k++) {
            ContentCatalog.CollabDef cd = find(defs, known[k][0]);
            expect(cd != null, "JAR CSV contains " + known[k][0]);
            if (cd == null) continue;
            expect(cd.weaponA.equals(known[k][1]),
                known[k][0] + " weaponA normalized to " + known[k][1] + " (got " + cd.weaponA + ")");
            expect(cd.weaponB.equals(known[k][2]),
                known[k][0] + " weaponB normalized to " + known[k][2] + " (got " + cd.weaponB + ")");
            expect(ContentCatalog.weapon(cd.weaponA) != null,
                known[k][0] + " weaponA resolves in the runtime catalog");
            expect(ContentCatalog.weapon(cd.weaponB) != null,
                known[k][0] + " weaponB resolves in the runtime catalog");
        }
    }

    private static ContentCatalog.CollabDef find(ContentCatalog.CollabDef[] defs, String id) {
        for (int i = 0; i < defs.length; i++) {
            if (defs[i].id.equals(id)) return defs[i];
        }
        return null;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}
