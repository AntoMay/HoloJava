package me.holojava.fullgame;

import java.io.File;
import java.io.FileInputStream;

/**
 * Pins the stage-BGM contract introduced by the audio wave: every stage maps to a MIDI under
 * assets/fullgame/audio except Arena Hutan Sawit (the project's own stage, deliberately silent —
 * it has no source track and was requested without music), each mapped file is a real MIDI, and
 * the whole set stays inside the size budget it was approved under.
 *
 * <p>The budget checks are the point of the suite: the source OGGs are 5-15 MB apiece and MIDI
 * was chosen (about 112 KB total) precisely so the jar cannot quietly grow back toward audio
 * bloat. A future track that exceeds a ceiling fails here, before packaging.
 *
 * <p>All checks are file-side and table-side; the MMAPI playback path itself is device- or
 * emulator-verified only, since BgmAudio's media calls cannot run headlessly.
 */
public final class BgmStageMusicTest {

    private static int checks = 0;
    private static int failures = 0;

    /** Per-track ceiling: no transcription in the tree is larger than this. */
    private static final int MAX_TRACK_BYTES = 16384;
    /** Whole-set ceiling for assets/fullgame/audio: the audio wave's size budget. */
    private static final int MAX_TOTAL_BYTES = 204800;

    public static void main(String[] args) throws Exception {
        testEveryStageHasMusicExceptHutanSawit();
        testMappedFilesAreRealMidis();
        testSizeBudget();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ------------------------------------------------------------------ tests

    /** [1] Every stage maps somewhere under assets/fullgame/audio; stage6 maps nowhere. */
    private static void testEveryStageHasMusicExceptHutanSawit() {
        System.out.println("[1] every stage except Arena Hutan Sawit maps to a midi");
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            String id = ContentCatalog.stage(i).id;
            String path = BgmAudio.musicFor(i);
            if ("stage6".equals(id)) {
                expect(path == null, "Arena Hutan Sawit stays silent, got " + path);
            } else {
                expect(path != null && path.startsWith("/assets/fullgame/audio/"),
                        "stage " + id + " maps under assets/fullgame/audio, got " + path);
            }
        }
    }

    /** [2] Every mapped file exists, is a real MIDI (MThd header), and is not a stub. */
    private static void testMappedFilesAreRealMidis() throws Exception {
        System.out.println("[2] each mapped file is a real midi");
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            String path = BgmAudio.musicFor(i);
            if (path == null) continue;
            File file = new File(path.substring(1)); // strip the resource-leading slash
            expect(file.isFile(), "midi exists: " + path);
            if (!file.isFile()) continue;
            byte[] head = new byte[4];
            FileInputStream in = new FileInputStream(file);
            int n = in.read(head);
            in.close();
            expect(n == 4 && head[0] == 'M' && head[1] == 'T' && head[2] == 'h' && head[3] == 'd',
                    "MThd header: " + path);
            expect(file.length() > 512, "track is not a stub: " + path);
        }
    }

    /** [3] Per-track and total size ceilings — the reason MIDI was chosen at all. */
    private static void testSizeBudget() throws Exception {
        System.out.println("[3] the midi set fits its size budget");
        long total = 0;
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            String path = BgmAudio.musicFor(i);
            if (path == null) continue;
            long length = new File(path.substring(1)).length();
            total += length;
            expect(length <= MAX_TRACK_BYTES,
                    path + " fits " + MAX_TRACK_BYTES + " B, got " + length);
        }
        expect(total <= MAX_TOTAL_BYTES,
                "all tracks fit " + MAX_TOTAL_BYTES + " B, got " + total);
    }

    // ---------------------------------------------------------------- helpers

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}
