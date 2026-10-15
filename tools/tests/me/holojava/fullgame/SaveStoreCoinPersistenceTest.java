package me.holojava.fullgame;

/**
 * Unit verification for permanent coin persistence in {@link SaveStore}.
 *
 * <p>Runs on a desktop JVM. It never touches {@code javax.microedition.rms}: the store is driven
 * through the injectable {@link SaveStore.RecordStorage} seam with an in-memory fake, because the
 * MIDP stub throws {@code Error("API Stub has been used")} outside a real device.
 *
 * <p>What this proves: record layout, old-save compatibility, the big-endian {@code long} codec,
 * malformed and truncated records, and the transaction rule that a failed write must leave the
 * reported balance untouched.
 *
 * <p>What this does <b>not</b> prove: that a real MIDP {@code RecordStore} actually commits these
 * bytes, and the actual on-device heap cost. Both still need an emulator or a device run.
 */
public final class SaveStoreCoinPersistenceTest {

    private static int checks = 0;
    private static int failures = 0;

    private static final int RECORD_ID = 1;
    private static final int OLD_DATA_LEN = 48;
    private static final int NEW_DATA_LEN = 56;
    private static final int SETTINGS_INDEX = 47;
    private static final int COIN_INDEX = 48;

    /** In-memory stand-in for {@code RecordStore}. A read failure and an absent record stay distinct,
     *  mirroring the production seam: {@code readFails} throws, a null record means absent.
     *  {@code failClose} models a store that reports success on write but cannot commit on close. */
    private static final class FakeStorage implements SaveStore.RecordStorage {
        byte[] record;
        boolean failWrites;
        boolean readFails;
        boolean failClose;
        int writeCount;

        FakeStorage(byte[] record) {
            this.record = record;
        }

        public byte[] read(int recordId) {
            if (readFails) throw new SaveStore.StorageFailure("simulated read failure");
            if (record == null) return null;
            return record.clone();
        }

        public boolean write(int recordId, byte[] payload) {
            writeCount++;
            if (failWrites) return false;
            if (failClose) return false;   // committed nothing, reports failure to the caller
            record = payload.clone();
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        testOldSaveLoadsWithZeroBalance();
        testSaveWithBalanceLoads();
        testCodecRoundTrip();
        testTruncatedAndUnexpectedLengths();
        testCorruptBalanceIsSanitised();
        testFailedWriteLeavesBalanceUnchanged();
        testOverflowRejectedWholesale();
        testOtherDataSurvivesCoinTransactions();
        testRecordLengthBoundaries();
        testNoRecordYieldsDefaults();
        testReadFailureIsNotANewRecord();
        testWritesRefusedAfterReadFailure();
        testRecoversAfterASuccessfulRead();
        testAbsentRecordStillInitialises();
        testCloseFailureIsNotReportedAsSuccess();
        testAbsenceTypeHierarchy();
        testMissingStoreAllowsNewSave();
        testOtherReadErrorStillBlocks();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- 1. Old 48-byte save keeps everything, balance starts at zero ------------

    private static void testOldSaveLoadsWithZeroBalance() {
        System.out.println("[1] old 48-byte save");
        byte[] old = new byte[OLD_DATA_LEN];
        old[0] = (byte) ((1 << 0) | (1 << 2));   // character 0 cleared stages 0 and 2
        old[1] = (byte) (1 << 1);                // character 1 cleared stage 1
        old[4] = (byte) (1 << 7);                // character 4 cleared stage 7
        old[SETTINGS_INDEX] = 0x41;             // hardcore on (bit 0), character 1 (bits 0-5)

        SaveStore store = new SaveStore(new FakeStorage(old));
        expectEq(store.coinBalance(), 0L, "old save starts at balance 0");
        expect(store.isCompleted(0, 0), "old save: char0/stage0 preserved");
        expect(store.isCompleted(0, 2), "old save: char0/stage2 preserved");
        expect(!store.isCompleted(0, 1), "old save: char0/stage1 still clear");
        expect(store.isCompleted(1, 1), "old save: char1/stage1 preserved");
        expect(store.isCompleted(4, 7), "old save: char4/stage7 preserved");
        expect(!store.isCompleted(1, 0), "old save: char1/stage0 still clear");
        expectEq(store.selectedCharacter(), 1, "old save: selected character preserved");
        expect(store.hardcore(), "old save: hardcore preserved");
    }

    // ---- 2. A record that already carries a balance loads it ---------------------

    private static void testSaveWithBalanceLoads() {
        System.out.println("[2] save carrying a balance");
        byte[] record = new byte[NEW_DATA_LEN];
        record[0] = (byte) (1 << 3);
        record[SETTINGS_INDEX] = 0x02;
        SaveStore.writeCoin(record, COIN_INDEX, 123456789L);

        SaveStore store = new SaveStore(new FakeStorage(record));
        expectEq(store.coinBalance(), 123456789L, "stored balance loads");
        expect(store.isCompleted(0, 3), "completion still loads alongside the balance");
        expectEq(store.selectedCharacter(), 2, "settings still load alongside the balance");

        SaveStore.writeCoin(record, COIN_INDEX, Long.MAX_VALUE);
        expectEq(new SaveStore(new FakeStorage(record)).coinBalance(), Long.MAX_VALUE,
            "MAX balance round-trips through storage");
        SaveStore.writeCoin(record, COIN_INDEX, 0L);
        expectEq(new SaveStore(new FakeStorage(record)).coinBalance(), 0L,
            "explicit zero balance round-trips");
    }

    // ---- 3. Codec round-trip ----------------------------------------------------

    private static void testCodecRoundTrip() {
        System.out.println("[3] codec round-trip");
        long[] values = { 0L, 1L, 10L, 255L, 256L, 65535L, 65536L, 2147483647L, 2147483648L,
                4294967296L, 1234567890123L, Long.MAX_VALUE - 1, Long.MAX_VALUE };
        for (int i = 0; i < values.length; i++) {
            byte[] buffer = new byte[NEW_DATA_LEN];
            SaveStore.writeCoin(buffer, COIN_INDEX, values[i]);
            expectEq(SaveStore.readCoin(buffer, COIN_INDEX), values[i], "round-trip " + values[i]);
        }
        // Byte order must be big-endian and independent of the host.
        byte[] buffer = new byte[NEW_DATA_LEN];
        SaveStore.writeCoin(buffer, COIN_INDEX, 0x0102030405060708L);
        expectEq(buffer[COIN_INDEX] & 0xff, 0x01, "big-endian: most significant byte first");
        expectEq(buffer[COIN_INDEX + 7] & 0xff, 0x08, "big-endian: least significant byte last");
        // The codec must not disturb anything outside its own eight bytes.
        byte[] guarded = new byte[NEW_DATA_LEN];
        for (int i = 0; i < NEW_DATA_LEN; i++) guarded[i] = (byte) 0xAB;
        SaveStore.writeCoin(guarded, COIN_INDEX, 42L);
        boolean outsideIntact = true;
        for (int i = 0; i < NEW_DATA_LEN; i++) {
            if (i >= COIN_INDEX && i < COIN_INDEX + 8) continue;
            if (guarded[i] != (byte) 0xAB) outsideIntact = false;
        }
        expect(outsideIntact, "codec leaves bytes outside the coin slot untouched");
        expectEq(SaveStore.readCoin(null, COIN_INDEX), 0L, "null record decodes to 0");
        SaveStore.writeCoin(null, COIN_INDEX, 5L);   // must not throw
        expect(true, "writeCoin on a null record is a no-op");
        byte[] tiny = new byte[4];
        SaveStore.writeCoin(tiny, 0, 7L);
        expectEq(tiny.length, 4, "writeCoin does not grow a short array");
    }

    // ---- 4. Truncated and unexpected record lengths ------------------------------

    private static void testTruncatedAndUnexpectedLengths() {
        System.out.println("[4] truncated / unexpected lengths");
        // 55 bytes: only seven of the eight coin bytes exist. The codec writes the most significant
        // byte at the lowest index, so indices 48..54 carry MSB..second-lowest and the missing
        // index 55 reads as zero, meaning a truncated record can only carry values whose lowest
        // byte is already zero. A 0x7F fill keeps the sign bit clear so the value stays a
        // positive long; a 0xFF fill would decode negative and be treated as corrupt instead.
        byte[] cut = new byte[NEW_DATA_LEN - 1];
        for (int i = 0; i < 7; i++) cut[COIN_INDEX + i] = (byte) 0x7F;
        expectEq(new SaveStore(new FakeStorage(cut)).coinBalance(), 0x7F7F7F7F7F7F7F00L,
            "55-byte record reads exactly the seven bytes it has");

        byte[] cutHighBit = new byte[NEW_DATA_LEN - 1];
        for (int i = 0; i < 7; i++) cutHighBit[COIN_INDEX + i] = (byte) 0xFF;
        expectEq(new SaveStore(new FakeStorage(cutHighBit)).coinBalance(), 0L,
            "55-byte record whose sign bit is set is treated as corrupt, not as a huge balance");

        // 49 bytes: only one coin byte present.
        byte[] tiny = new byte[COIN_INDEX + 1];
        expectEq(new SaveStore(new FakeStorage(tiny)).coinBalance(), 0L,
            "49-byte record yields balance 0");

        // 47 bytes: settings byte is missing, so settings fall back to defaults, balance 0.
        byte[] noSettings = new byte[SETTINGS_INDEX];
        expectEq(new SaveStore(new FakeStorage(noSettings)).coinBalance(), 0L,
            "47-byte record yields balance 0");

        // 6 bytes: the legacy path must still transpose and must not invent a balance.
        byte[] legacy = new byte[6];
        legacy[1] = (byte) (1 << 2);
        SaveStore legacyStore = new SaveStore(new FakeStorage(legacy));
        expectEq(legacyStore.coinBalance(), 0L, "legacy 6-byte record yields balance 0");
        expect(legacyStore.isCompleted(2, 1), "legacy record still transposes correctly");

        // Longer than the layout: truncated to DATA_LEN, highest byte dropped.
        byte[] over = new byte[NEW_DATA_LEN + 64];
        SaveStore.writeCoin(over, COIN_INDEX, Long.MAX_VALUE);
        expectEq(new SaveStore(new FakeStorage(over)).coinBalance(), Long.MAX_VALUE,
            "over-long record still loads the balance from the first 56 bytes");

        // Empty record.
        expectEq(new SaveStore(new FakeStorage(new byte[0])).coinBalance(), 0L,
            "zero-length record yields balance 0");
    }

    // ---- 5. Corrupt / negative balance is sanitised ------------------------------

    private static void testCorruptBalanceIsSanitised() {
        System.out.println("[5] corrupt balance sanitised");
        byte[] negative = new byte[NEW_DATA_LEN];
        for (int i = 0; i < 8; i++) negative[COIN_INDEX + i] = (byte) 0xff;
        expectEq(SaveStore.readCoin(negative, COIN_INDEX), -1L, "all-ones decodes to -1");
        expectEq(new SaveStore(new FakeStorage(negative)).coinBalance(), 0L,
            "corrupt negative balance reports 0");

        // Sanitising on read must not rewrite the stored bytes.
        FakeStorage storage = new FakeStorage(negative);
        SaveStore store = new SaveStore(storage);
        store.coinBalance();
        boolean stillNegative = true;
        for (int i = 0; i < 8; i++) {
            if (storage.record[COIN_INDEX + i] != (byte) 0xff) stillNegative = false;
        }
        expect(stillNegative, "reading a corrupt record does not rewrite it");

        // Crediting onto a corrupt record must start from a clean zero, not from -1.
        expect(store.creditCoins(10L), "credit onto a corrupt record commits");
        expectEq(store.coinBalance(), 10L, "credit onto a corrupt record starts from 0");
    }

    // ---- 6. A failed write must not change the reported balance ------------------

    private static void testFailedWriteLeavesBalanceUnchanged() {
        System.out.println("[6] failed write keeps the balance");
        FakeStorage storage = new FakeStorage(null);
        SaveStore store = new SaveStore(storage);
        expectEq(store.coinBalance(), 0L, "starts at 0");

        expect(store.creditCoins(100L), "first credit succeeds");
        expectEq(store.coinBalance(), 100L, "balance is 100 after a committed credit");

        storage.failWrites = true;
        expect(!store.creditCoins(50L), "a failed commit reports failure");
        expectEq(store.coinBalance(), 100L, "balance unchanged after a failed commit");

        expect(!store.spendCoins(30L), "a failed spend reports failure");
        expectEq(store.coinBalance(), 100L, "balance unchanged after a failed spend");

        // Repeated failures must not accumulate hidden credit.
        for (int i = 0; i < 5; i++) store.creditCoins(10L);
        expectEq(store.coinBalance(), 100L, "five failed credits leave the balance at 100");

        // A later successful write must reflect only what actually committed.
        storage.failWrites = false;
        expect(store.creditCoins(25L), "credit succeeds once storage recovers");
        expectEq(store.coinBalance(), 125L, "balance is 100 + the one committed 25");

        // The bytes on disk must match the reported balance exactly.
        expectEq(SaveStore.readCoin(storage.record, COIN_INDEX), 125L,
            "persisted bytes agree with the reported balance");
        expectEq(new SaveStore(new FakeStorage(storage.record)).coinBalance(), 125L,
            "a fresh store reads back the same balance");
    }

    // ---- 7. Overflow is refused as a whole --------------------------------------

    private static void testOverflowRejectedWholesale() {
        System.out.println("[7] overflow refused wholesale");
        FakeStorage storage = new FakeStorage(null);
        SaveStore store = new SaveStore(storage);

        expect(!store.creditCoins(-1L), "negative credit refused");
        expectEq(store.coinBalance(), 0L, "refused negative credit leaves 0");
        expect(!store.creditCoins(0L), "zero credit refused");
        expectEq(store.coinBalance(), 0L, "refused zero credit leaves 0");

        expect(store.creditCoins(Long.MAX_VALUE - 5L), "credit close to the ceiling commits");
        expectEq(store.coinBalance(), Long.MAX_VALUE - 5L, "balance is MAX-5");

        int writesBefore = storage.writeCount;
        expect(!store.creditCoins(10L), "an overflowing credit is refused");
        expectEq(store.coinBalance(), Long.MAX_VALUE - 5L, "no partial credit was kept");
        expectEq(storage.writeCount, writesBefore, "a refused overflow performs no write at all");
        expectEq(SaveStore.readCoin(storage.record, COIN_INDEX), Long.MAX_VALUE - 5L,
            "stored bytes unchanged after the refused overflow");

        expect(!store.spendCoins(Long.MAX_VALUE), "over-spending is refused");
        expectEq(store.coinBalance(), Long.MAX_VALUE - 5L, "balance unchanged after refusal");
        expect(store.spendCoins(Long.MAX_VALUE - 5L), "spending the exact balance succeeds");
        expectEq(store.coinBalance(), 0L, "balance drained to 0");
    }

    // ---- 8. Coin transactions must not disturb other record bytes ----------------

    private static void testOtherDataSurvivesCoinTransactions() {
        System.out.println("[8] other record bytes survive");
        byte[] record = new byte[NEW_DATA_LEN];
        record[0] = (byte) 0xff;                  // character 0: every stage cleared
        record[1] = (byte) 0x0f;
        record[SETTINGS_INDEX] = 0x45;           // hardcore on, character 5
        FakeStorage storage = new FakeStorage(record);
        SaveStore store = new SaveStore(storage);

        store.creditCoins(777L);
        store.creditCoins(3L);
        store.spendCoins(80L);

        expectEq(store.coinBalance(), 700L, "balance arithmetic holds across transactions");
        expectEq(storage.record[0] & 0xff, 0xff, "character 0 bytes untouched");
        expectEq(storage.record[1] & 0xff, 0x0f, "character 1 bytes untouched");
        expectEq(storage.record[SETTINGS_INDEX] & 0xff, 0x45, "settings byte untouched");

        SaveStore reopened = new SaveStore(new FakeStorage(storage.record));
        expect(reopened.isCompleted(0, 5), "completion survives a reopen");
        expectEq(reopened.selectedCharacter(), 5, "settings survive a reopen");
        expect(reopened.hardcore(), "hardcore survives a reopen");
        expectEq(reopened.coinBalance(), 700L, "balance survives a reopen");
    }

    // ---- 9. Record length boundaries --------------------------------------------

    private static void testRecordLengthBoundaries() {
        System.out.println("[9] record length boundaries");
        for (int len : new int[] { 0, 1, 5, 6, 7, 47, 48, 49, 55, 56, 57, 200 }) {
            byte[] record = new byte[len];
            SaveStore store = new SaveStore(new FakeStorage(record));
            expect(store.coinBalance() >= 0, "length " + len + " yields a usable balance");
        }
        // Exactly 56 is the first length that can carry a full balance.
        byte[] exact = new byte[NEW_DATA_LEN];
        SaveStore.writeCoin(exact, COIN_INDEX, 1L);
        expectEq(new SaveStore(new FakeStorage(exact)).coinBalance(), 1L,
            "56 bytes is the first length that carries a balance");
        // One byte short: only the seven bytes at indices 48..54 survive, so a value is representable
        // exactly when its low byte is zero. The codec drops the out-of-range byte silently, which
        // is what makes this a truncation case rather than a corruption case.
        byte[] short56 = new byte[NEW_DATA_LEN - 1];
        for (int i = 0; i < 7; i++) short56[COIN_INDEX + i] = (byte) 0x01;
        expectEq(new SaveStore(new FakeStorage(short56)).coinBalance(), 0x0101010101010100L,
            "a 55-byte record carries a value whose lowest byte is zero");
        byte[] short56Lost = new byte[NEW_DATA_LEN - 1];
        SaveStore.writeCoin(short56Lost, COIN_INDEX, 1L);
        expectEq(new SaveStore(new FakeStorage(short56Lost)).coinBalance(), 0L,
            "a 55-byte record cannot carry a value needing its 8th byte");
    }

    // ---- 10. No record at all ----------------------------------------------------

    private static void testNoRecordYieldsDefaults() {
        System.out.println("[10] absent record");
        SaveStore store = new SaveStore(new FakeStorage(null));
        expectEq(store.coinBalance(), 0L, "no record yields balance 0");
        expect(!store.isCompleted(0, 0), "no record yields no completions");
        expectEq(store.selectedCharacter(), 0, "no record yields character 0");
        expect(!store.hardcore(), "no record yields hardcore off");
        expect(store.creditCoins(1L), "crediting onto an absent record commits");
        expectEq(store.coinBalance(), 1L, "balance is 1 after the first commit");
    }

    // ---- 11. A read failure is not an absent record ------------------------------

    private static void testReadFailureIsNotANewRecord() {
        System.out.println("[11] read failure is not a new record");
        byte[] good = new byte[NEW_DATA_LEN];
        good[0] = (byte) 0xff;              // real progress on disk
        good[SETTINGS_INDEX] = 0x41;
        SaveStore.writeCoin(good, COIN_INDEX, 500L);

        FakeStorage broken = new FakeStorage(good.clone());
        broken.readFails = true;
        SaveStore store = new SaveStore(broken);
        expectEq(store.loadState(), SaveStore.LOAD_FAILED, "a read error reports LOAD_FAILED");

        // Absent and failed must be distinguishable, or a transient error looks like a new install.
        SaveStore absent = new SaveStore(new FakeStorage(null));
        expectEq(absent.loadState(), SaveStore.LOAD_ABSENT, "no record reports LOAD_ABSENT");
        expect(absent.loadState() != store.loadState(), "absent and failed are different states");

        SaveStore healthy = new SaveStore(new FakeStorage(good.clone()));
        expectEq(healthy.loadState(), SaveStore.LOAD_LOADED, "a good record reports LOAD_LOADED");
        expect(healthy.loadState() != store.loadState(), "loaded and failed are different states");
        expectEq(healthy.coinBalance(), 500L, "the healthy read still sees the balance");
    }

    // ---- 12. Nothing may be written while the record is unknown ------------------

    private static void testWritesRefusedAfterReadFailure() {
        System.out.println("[12] writes refused after a read failure");
        byte[] good = new byte[NEW_DATA_LEN];
        good[0] = (byte) 0xff;
        good[1] = (byte) 0x0f;
        good[SETTINGS_INDEX] = 0x41;
        SaveStore.writeCoin(good, COIN_INDEX, 500L);
        byte[] pristine = good.clone();

        FakeStorage broken = new FakeStorage(good.clone());
        broken.readFails = true;
        SaveStore store = new SaveStore(broken);
        expectEq(store.loadState(), SaveStore.LOAD_FAILED, "store is in the failed state");

        expect(!store.creditCoins(10L), "credit is refused after a read failure");
        expect(!store.spendCoins(10L), "spend is refused after a read failure");
        expectEq(store.coinBalance(), 0L, "the reported balance is the empty default, not real progress");

        // The legacy mutators must not write either.
        store.setCompleted(0, 1);
        store.setHardcore(true);
        store.setSelectedCharacter(2);

        expectEq(broken.writeCount, 0, "not a single write reached storage");
        boolean untouched = true;
        for (int i = 0; i < pristine.length; i++) {
            if (broken.record[i] != pristine[i]) untouched = false;
        }
        expect(untouched, "the stored record is byte-for-byte unchanged");

        // Repeated attempts must stay refused.
        for (int i = 0; i < 5; i++) store.creditCoins(1L);
        expectEq(broken.writeCount, 0, "repeated credits still produce no write");

        // The failure state persists for the life of this instance.
        expectEq(store.loadState(), SaveStore.LOAD_FAILED, "the error state is retained");
    }

    // ---- 13. Recovery once a valid read happens ----------------------------------

    private static void testRecoversAfterASuccessfulRead() {
        System.out.println("[13] recovery after a successful read");
        byte[] good = new byte[NEW_DATA_LEN];
        good[0] = (byte) 0xff;
        good[SETTINGS_INDEX] = 0x41;
        SaveStore.writeCoin(good, COIN_INDEX, 500L);

        FakeStorage shared = new FakeStorage(good.clone());
        shared.readFails = true;
        new SaveStore(shared);                 // a session that could not read
        expectEq(shared.record.length, NEW_DATA_LEN, "the record is untouched by the failed session");

        // A later session that can read sees the real progress and may write again.
        shared.readFails = false;
        SaveStore recovered = new SaveStore(shared);
        expectEq(recovered.loadState(), SaveStore.LOAD_LOADED, "the new session loads normally");
        expect(recovered.isCompleted(0, 0), "real progress survived the failed session");
        expectEq(recovered.selectedCharacter(), 1, "settings survived the failed session");
        expectEq(recovered.coinBalance(), 500L, "balance survived the failed session");
        expect(recovered.creditCoins(25L), "credit works again after recovery");
        expectEq(recovered.coinBalance(), 525L, "balance is 500 + 25");
        expect(shared.writeCount > 0, "writes reach storage again after recovery");
        expect(recovered.isCompleted(0, 0), "progress is still intact after the recovered credit");
    }

    // ---- 14. A genuinely absent record may still be initialised ------------------

    private static void testAbsentRecordStillInitialises() {
        System.out.println("[14] absent record still initialises");
        FakeStorage empty = new FakeStorage(null);
        SaveStore store = new SaveStore(empty);
        expectEq(store.loadState(), SaveStore.LOAD_ABSENT, "new install reports LOAD_ABSENT");
        expect(store.creditCoins(7L), "a new install may create the record");
        expectEq(store.coinBalance(), 7L, "the created record holds the balance");
        expectEq(empty.record.length, NEW_DATA_LEN, "the created record is the full layout");
        expectEq(SaveStore.readCoin(empty.record, COIN_INDEX), 7L, "the balance is on disk");

        store.setCompleted(1, 3);
        expectEq(empty.record.length, NEW_DATA_LEN, "legacy mutators still write on a new install");
    }

    // ---- 15. A close-time failure must not be reported as success -----------------

    private static void testCloseFailureIsNotReportedAsSuccess() {
        System.out.println("[15] close failure is not success");
        FakeStorage closeFails = new FakeStorage(null);
        SaveStore store = new SaveStore(closeFails);

        closeFails.failClose = true;
        expect(!store.creditCoins(50L), "a store that cannot commit reports failure");
        expectEq(store.coinBalance(), 0L, "the balance did not advance on a failed commit");
        expectEq(closeFails.writeCount, 1, "the write was attempted exactly once");

        closeFails.failClose = false;
        expect(store.creditCoins(50L), "the same transaction succeeds once close works");
        expectEq(store.coinBalance(), 50L, "the balance advanced only on the successful commit");
        expectEq(SaveStore.readCoin(closeFails.record, COIN_INDEX), 50L, "and it is genuinely on disk");
    }

    // ---- 16. The type relationship the absence fix relies on ------------------------

    /**
     * {@code SaveStore.isAbsence} matches two real {@code javax.microedition.rms} types with
     * {@code instanceof}. Those classes cannot be instantiated here: the pinned MIDP stub throws
     * {@code Error("API Stub has been used")} from every constructor, so the predicate cannot be
     * executed against real instances on this JVM. What <em>can</em> be verified without instances
     * is the precondition the match depends on, namely that both absence types are distinct
     * siblings under {@code RecordStoreException} and that no other RMS failure is one of them.
     * If that hierarchy ever changed, the predicate would need revisiting.
     */
    private static void testAbsenceTypeHierarchy() throws Exception {
        System.out.println("[16] absence type hierarchy");
        Class<?> base = Class.forName("javax.microedition.rms.RecordStoreException");
        Class<?> recordMissing = Class.forName("javax.microedition.rms.InvalidRecordIDException");
        Class<?> storeMissing = Class.forName("javax.microedition.rms.RecordStoreNotFoundException");

        expect(base.isAssignableFrom(recordMissing),
            "InvalidRecordIDException is a RecordStoreException, so the catch can see it");
        expect(base.isAssignableFrom(storeMissing),
            "RecordStoreNotFoundException is a RecordStoreException, so the catch can see it");
        expect(!recordMissing.isAssignableFrom(storeMissing),
            "RecordStoreNotFoundException is not an InvalidRecordIDException");
        expect(!storeMissing.isAssignableFrom(recordMissing),
            "InvalidRecordIDException is not a RecordStoreNotFoundException");
        expect(recordMissing != base && storeMissing != base,
            "both absence types are distinct from the base type");

        // Other RMS failures must not be mistaken for absence, so neither may match them.
        String[] others = { "RecordStoreNotOpenException", "RecordStoreFullException" };
        for (int i = 0; i < others.length; i++) {
            Class<?> c = Class.forName("javax.microedition.rms." + others[i]);
            expect(!recordMissing.isAssignableFrom(c) && !storeMissing.isAssignableFrom(c),
                others[i] + " is neither absence type");
        }

        // The stub must keep refusing construction, otherwise these tests would silently start
        // exercising a different classpath than the one the device uses.
        boolean stubRefused = false;
        try {
            storeMissing.getConstructor(String.class).newInstance("probe");
        } catch (Throwable refused) {
            stubRefused = true;
        }
        expect(stubRefused, "MIDP stub constructors stay unusable off-device, as assumed here");
    }

    // ---- 17. A store that does not exist yet must allow a new save ---------------

    /**
     * Reproduces the regression this task fixes: {@code openRecordStore(name, false)} on a device
     * with no store yet raises RecordStoreNotFoundException, and the read path used to turn that
     * into a hard read failure, permanently blocking a brand-new player from saving anything.
     */
    private static void testMissingStoreAllowsNewSave() {
        System.out.println("[17] missing store allows a new save");
        FakeStorage storage = new FakeStorage(null);
        SaveStore store = new SaveStore(storage);
        expectEq(store.loadState(), SaveStore.LOAD_ABSENT, "no record yet -> LOAD_ABSENT");

        expect(store.creditCoins(120L), "a brand-new player can be credited");
        expectEq(store.coinBalance(), 120L, "the balance was created");
        store.setCompleted(0, 2);
        expect(store.isCompleted(0, 2), "progress can be recorded for a new player");
        store.setHardcore(true);
        store.setSelectedCharacter(3);
        expectEq(store.selectedCharacter(), 3, "settings can be chosen for a new player");
        expect(store.hardcore(), "hardcore can be toggled for a new player");
        expectEq(storage.record.length, NEW_DATA_LEN, "the new save uses the full record layout");

        SaveStore reopened = new SaveStore(new FakeStorage(storage.record.clone()));
        expectEq(reopened.coinBalance(), 120L, "the new player's save survives a restart");
        expect(reopened.isCompleted(0, 2), "progress survives a restart");
        expectEq(reopened.selectedCharacter(), 3, "settings survive a restart");
    }

    // ---- 18. Any other read error must still block writes -------------------------

    private static void testOtherReadErrorStillBlocks() {
        System.out.println("[18] other read errors still block");
        byte[] good = new byte[NEW_DATA_LEN];
        good[0] = (byte) 0xff;
        good[SETTINGS_INDEX] = 0x41;
        SaveStore.writeCoin(good, COIN_INDEX, 750L);
        byte[] pristine = good.clone();

        FakeStorage broken = new FakeStorage(good.clone());
        broken.readFails = true;           // a plain RecordStoreException, not an absence
        SaveStore store = new SaveStore(broken);
        expectEq(store.loadState(), SaveStore.LOAD_FAILED, "a real read error still fails");

        expect(!store.creditCoins(5L), "credit refused on a genuine read error");
        expect(!store.spendCoins(5L), "spend refused on a genuine read error");
        store.setCompleted(1, 1);
        expectEq(broken.writeCount, 0, "nothing was written");

        boolean untouched = true;
        for (int i = 0; i < pristine.length; i++) {
            if (broken.record[i] != pristine[i]) untouched = false;
        }
        expect(untouched, "stored progress, settings and balance are unchanged");
    }

    // ---- helpers ----------------------------------------------------------------

    private static void expectEq(long got, long want, String label) {
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