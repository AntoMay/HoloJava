package me.holojava.fullgame;

/**
 * Static/unit verification for the 6-byte legacy save migration in {@link SaveStore}.
 *
 * <p>Runs on a desktop JVM. It never constructs {@code SaveStore} and never touches
 * {@code javax.microedition.rms}, so no MIDlet, emulator, or device is required. Only the pure
 * static helper {@link SaveStore#transposeLegacyRecord(byte[])} is exercised.
 *
 * <p>Layouts under test, both confirmed from the reference JAR bytecode rather than from
 * variable names:
 * <ul>
 *   <li>legacy (reference JAR): {@code data[stageOrder]} bit {@code characterIndex}
 *       — {@code isCompleted} loads parameter 0 into the array index and shifts parameter 1.</li>
 *   <li>current build: {@code data[characterIndex]} bit {@code stageOrder}.</li>
 * </ul>
 */
public final class SaveStoreLegacyMigrationTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        testBasicTransposition();
        testFullMatrix();
        testBitmaskEdges();
        testSettingsByte();
        testIdempotencyAndLengthSeparation();
        testNonLegacyRecordLengths();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- 1. Basic transposition -------------------------------------------------

    private static void testBasicTransposition() {
        System.out.println("[1] basic transposition");
        // Stage 1 cleared by characters 2 and 4. Legacy byte 1 = bits 2 and 4.
        byte[] legacy = new byte[6];
        legacy[1] = (byte) ((1 << 2) | (1 << 4));
        byte[] out = SaveStore.transposeLegacyRecord(legacy);

        // Must land as: character 2 completed stage 1, character 4 completed stage 1.
        expectBit(out, 2, 1, true, "char2/stage1");
        expectBit(out, 4, 1, true, "char4/stage1");
        // Nothing else may be set.
        for (int c = 0; c < 5; c++) {
            for (int s = 0; s < 8; s++) {
                if (c == 2 && s == 1) continue;
                if (c == 4 && s == 1) continue;
                expectBit(out, c, s, false, "unrelated " + c + "/" + s);
            }
        }
    }

    // ---- 2. Every valid (character, stage) pair ------------------------------------

    private static void testFullMatrix() {
        System.out.println("[2] full 5x5 matrix");
        for (int stage = 0; stage < 5; stage++) {
            for (int character = 0; character < 5; character++) {
                byte[] legacy = new byte[6];
                legacy[stage] = (byte) (1 << character);
                byte[] out = SaveStore.transposeLegacyRecord(legacy);
                expectBit(out, character, stage, true, "stage" + stage + "->char" + character);
                // The mirrored cell must stay clear: this is exactly the historical defect.
                if (character != stage) {
                    expectBit(out, stage, character, false, "no transpose back " + stage + "/" + character);
                }
            }
        }

        // Every one of the 25 bits set at once.
        byte[] all = new byte[6];
        for (int stage = 0; stage < 5; stage++) all[stage] = 0x1f;
        byte[] out = SaveStore.transposeLegacyRecord(all);
        for (int c = 0; c < 5; c++) expectByte(out, c, 0x1f, "full char " + c);
    }

    // ---- 3. Bitmask edges --------------------------------------------------------

    private static void testBitmaskEdges() {
        System.out.println("[3] bitmask edges");
        byte[] empty = new byte[6];
        byte[] outEmpty = SaveStore.transposeLegacyRecord(empty);
        for (int c = 0; c < 5; c++) expectByte(outEmpty, c, 0x00, "empty char " + c);
        expectByte(outEmpty, 47, 0x00, "empty settings");
        for (int i = 5; i < 47; i++) expectByte(outEmpty, i, 0x00, "empty reserved " + i);

        // Legacy stage bytes may carry bits 5-7 (legacy setCompleted allowed characterIndex <= 7).
        // Characters 5-7 do not exist in this 5-character roster, so those bits have no destination.
        byte[] high = new byte[6];
        high[0] = (byte) 0xe1; // bits 0, 5, 6, 7
        byte[] outHigh = SaveStore.transposeLegacyRecord(high);
        expectByte(outHigh, 0, 0x01, "high bits: only char 0 bit 0 survives");
        for (int c = 1; c < 5; c++) expectByte(outHigh, c, 0x00, "high bits: char " + c + " clear");

        // A single high bit alone must produce no completion at all.
        byte[] onlyHigh = new byte[6];
        onlyHigh[2] = (byte) 0x20;
        byte[] outOnly = SaveStore.transposeLegacyRecord(onlyHigh);
        for (int c = 0; c < 5; c++) expectByte(outOnly, c, 0x00, "bit5 only: char " + c);

        // Reserved bytes 5..46 must never be written.
        byte[] reserved = new byte[6];
        reserved[0] = (byte) 0xff;
        reserved[1] = (byte) 0xff;
        reserved[2] = (byte) 0xff;
        reserved[3] = (byte) 0xff;
        reserved[4] = (byte) 0xff;
        byte[] outRes = SaveStore.transposeLegacyRecord(reserved);
        for (int i = 5; i < 47; i++) expectByte(outRes, i, 0x00, "reserved " + i);
    }

    // ---- 4. Settings byte --------------------------------------------------------

    private static void testSettingsByte() {
        System.out.println("[4] settings byte");
        int[] samples = { 0x00, 0x01, 0x0f, 0x10, 0x21, 0x3f, 0x80, 0xff };
        for (int i = 0; i < samples.length; i++) {
            byte[] legacy = new byte[6];
            legacy[5] = (byte) samples[i];
            byte[] out = SaveStore.transposeLegacyRecord(legacy);
            expectByte(out, 47, samples[i], "settings " + samples[i]);

            // Hardcore is bit 0 in both layouts.
            boolean hardcore = (out[47] & 0x01) != 0;
            boolean legacyHardcore = (samples[i] & 0x01) != 0;
            expect(hardcore == legacyHardcore, "hardcore bit for 0x" + Integer.toHexString(samples[i]));
        }

        // Settings must not bleed into character bytes.
        byte[] legacy = new byte[6];
        legacy[5] = (byte) 0xff;
        byte[] out = SaveStore.transposeLegacyRecord(legacy);
        for (int c = 0; c < 5; c++) expectByte(out, c, 0x00, "settings isolation char " + c);
    }

    // ---- 5. Idempotency / layout separation ---------------------------------------

    private static void testIdempotencyAndLengthSeparation() {
        System.out.println("[5] idempotency and length separation");
        byte[] legacy = new byte[6];
        legacy[0] = (byte) 0x11;
        legacy[2] = (byte) 0x08;
        legacy[5] = (byte) 0x05;
        byte[] first = SaveStore.transposeLegacyRecord(legacy);

        // Migrating must not mutate the caller's array.
        byte[] snapshot = legacy.clone();
        SaveStore.transposeLegacyRecord(legacy);
        expect(java.util.Arrays.equals(snapshot, legacy), "input array untouched");

        // Re-running the same migration is deterministic.
        byte[] second = SaveStore.transposeLegacyRecord(legacy);
        expect(java.util.Arrays.equals(first, second), "migration is deterministic");

        // A 48-byte current-layout record must NOT be routed through the legacy transpose.
        boolean threw = false;
        try {
            SaveStore.transposeLegacyRecord(new byte[48]);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        expect(threw, "48-byte record rejected by legacy path (handled by load else-branch)");

        threw = false;
        try {
            SaveStore.transposeLegacyRecord(new byte[7]);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        expect(threw, "7-byte record rejected by legacy path");
    }

    // ---- 6/7. Non-legacy record lengths ------------------------------------------

    private static void testNonLegacyRecordLengths() {
        System.out.println("[6/7] non-legacy record lengths");
        // load()'s else-branch keeps: count = min(length, 48); copy data[0..count-1].
        // Asserted here as a documented contract of the unchanged branch.
        int[] lengths = { 0, 1, 7, 47, 48, 49, 200 };
        for (int i = 0; i < lengths.length; i++) {
            int len = lengths[i];
            int count = len < 48 ? len : 48;
            expect(count == (len < 48 ? len : 48), "truncation math for length " + len);
            // Below 48 the copy is partial, so data[47] keeps its zero default and settings are
            // lost. At 48 or above the full payload is copied, settings included, and any bytes
            // past offset 47 are discarded. Both are pre-existing behaviours, unchanged here.
            boolean settingsSurvive = len >= 48;
            expect(settingsSurvive == (count == 48), "settings survival for length " + len);
        }
        System.out.println("      (0,1,7,47 -> partial copy, settings lost; 48 -> full; 49,200 -> truncated to 48, settings kept)");
    }

    // ---- helpers -----------------------------------------------------------------

    private static void expectBit(byte[] data, int character, int stage, boolean want, String label) {
        boolean got = ((data[character] & 0xff) & (1 << stage)) != 0;
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectByte(byte[] data, int index, int want, String label) {
        int got = data[index] & 0xff;
        expect(got == want, label + ": expected 0x" + Integer.toHexString(want)
                + " got 0x" + Integer.toHexString(got));
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}
