package me.holojava.fullgame;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

/** Optional RMS persistence. Run gameplay never depends on persistence succeeding. */
final class SaveStore {
    private static final String STORE = "holojava_fullgame_v1";
    private static final int RECORD_ID = 1;
    // Bytes 0..46 are the per-character completion map (bit = stage order), byte 47 is settings,
    // and bytes 48..55 are the permanent global coin balance. Growing DATA_LEN is the migration:
    // load() copies min(record length, DATA_LEN), so older shorter records load in full and the
    // bytes they never had stay zero, which decodes to a balance of 0. No store rename, no reset.
    private static final int DATA_LEN = 56;
    private static final int SETTINGS_INDEX = 47;
    private static final int COIN_INDEX = 48;
    private static final int COIN_LEN = 8;
    private static final int LEGACY_LEN = 6;
    private static final int LEGACY_STAGES = 5;   // legacy isCompleted required stageOrder < 5.
    private static final int LEGACY_CHARS = 5;    // legacy roster had 5 characters (bits 0-4).
    private static final int LEGACY_SETTINGS_INDEX = 5;

    // Load outcome. ABSENT and LOADED both mean the in-memory record is trustworthy and may be
    // written back. FAILED means the record could not be read at all, so the in-memory record is
    // empty defaults that must never be written back over real progress.
    static final int LOAD_ABSENT = 0;   // store has no record yet; a new install, safe to initialise
    static final int LOAD_LOADED = 1;   // record read successfully
    static final int LOAD_FAILED = 2;   // storage error; state is unknown and writes are refused

    private final byte[] data = new byte[DATA_LEN];
    private final RecordStorage storage;
    private int loadState = LOAD_ABSENT;

    /**
     * The single seam that touches {@code javax.microedition.rms}. Everything above it is plain
     * byte[] work, so the record layout and the transaction rules can be exercised on a desktop
     * JVM with a fake implementation, where the MIDP stub would otherwise throw.
     */
    static interface RecordStorage {
        /**
         * Reads the stored record.
         *
         * @return the record, or null when the store holds no such record
         * @throws StorageFailure when the read could not be completed; a caller must not treat that
         *         as an absent record, because it means the stored progress is unknown
         */
        byte[] read(int recordId);
        /** Writes the record. Returns false when the write could not be committed. */
        boolean write(int recordId, byte[] payload);
    }

    /**
     * Opens a record store by name. Split out from {@link RecordStorage} so the RMS adapter's
     * exception-to-outcome mapping can be exercised directly: the real {@code RecordStore} cannot
     * be constructed off-device, but the failure that opening raises is exactly what decides
     * whether a new install may be initialised.
     *
     * <p>Production always uses {@link RecordStore#openRecordStore(String, boolean)}.
     */
    static interface RecordStoreOpener {
        /**
         * @param name record store name
         * @param createIfMissing whether to create the store when it does not exist
         * @return the opened store, never null
         * @throws RecordStoreException when the store cannot be opened, including
         *         {@code RecordStoreNotFoundException} when it does not exist and may not be created
         */
        RecordStore open(String name, boolean createIfMissing) throws RecordStoreException;
    }

    /** The production opener: a direct call to the static RMS factory. */
    private static final class RealRecordStoreOpener implements RecordStoreOpener {
        public RecordStore open(String name, boolean createIfMissing) throws RecordStoreException {
            return RecordStore.openRecordStore(name, createIfMissing);
        }
    }

    /** Signals that a record could not be read, as opposed to being absent. */
    static final class StorageFailure extends RuntimeException {
        StorageFailure(String message) {
            super(message);
        }
    }

    /**
     * True when a storage failure only means the data is not there yet, so a fresh save may be
     * started: either the store itself was never created, or the store exists but holds no such
     * record. Any other failure means the stored progress is unknown and must not be overwritten.
     *
     * <p>Both absence types are siblings under {@code RecordStoreException}, so they are matched
     * by type rather than by catch order; catching them by name would let the generic clause
     * swallow one of them silently if the order ever changed.
     *
     * @param problem the exception raised while opening the store or reading the record
     * @return true only for the two "not there yet" cases
     */
    static boolean isAbsence(RecordStoreException problem) {
        return problem instanceof RecordStoreNotFoundException
                || problem instanceof InvalidRecordIDException;
    }

    SaveStore() {
        this(new RmsRecordStorage());
    }

    /** Test seam. Production code uses the no-argument constructor. */
    SaveStore(RecordStorage storage) {
        this.storage = storage == null ? new RmsRecordStorage() : storage;
        for (int i = 0; i < DATA_LEN; i++) data[i] = 0;
        load();
    }

    /** One of {@link #LOAD_ABSENT}, {@link #LOAD_LOADED}, {@link #LOAD_FAILED}. */
    int loadState() {
        return loadState;
    }

    boolean isCompleted(int characterIndex, int stageOrder) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return false;
        int v = data[characterIndex] & 0xff;
        return (v & (1 << stageOrder)) != 0;
    }

    void setCompleted(int characterIndex, int stageOrder) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return;
        int v = data[characterIndex] & 0xff;
        data[characterIndex] = (byte) (v | (1 << stageOrder));
        save();
    }

    boolean hardcore() { return (data[SETTINGS_INDEX] & 1) != 0; }
    void setHardcore(boolean value) {
        int v = data[SETTINGS_INDEX] & 0xff;
        if (value) v |= 1; else v &= ~1;
        data[SETTINGS_INDEX] = (byte) v;
        save();
    }

    int selectedCharacter() { return data[SETTINGS_INDEX] & 0x3f; }

    void setSelectedCharacter(int value) {
        int settings = data[SETTINGS_INDEX] & 0xc0;
        data[SETTINGS_INDEX] = (byte) (settings | (value & 0x3f));
        save();
    }

    // ---- permanent global coin balance ----------------------------------------

    /**
     * The permanent coin balance shared by every character, read from the record on each call so
     * there is exactly one source of truth and it can never drift out of sync with the bytes.
     *
     * <p>A corrupted record whose bytes decode to a negative number is sanitised to zero here
     * rather than at load time, so the stored bytes stay exactly as written and no data is
     * silently rewritten just by being read.
     */
    long coinBalance() {
        return CoinBalance.sanitize(readCoin(data, COIN_INDEX));
    }

    /**
     * Credits coins and persists them as one transaction.
     *
     * <p>Overflow is rejected as a whole: if the amount does not fit, nothing is credited and
     * nothing is written, so a caller never receives a partial deposit. The in-memory record is
     * updated only after the write has been reported successful, which means a failed write
     * leaves the balance exactly as it was instead of silently reverting later.
     *
     * @param amount coins to credit; must be positive and must fit
     * @return true when the balance was credited and the record committed
     */
    boolean creditCoins(long amount) {
        long current = coinBalance();
        if (!CoinBalance.canAdd(current, amount)) return false;
        return commitCoin(CoinBalance.add(current, amount));
    }

    /**
     * Pays coins out of the balance and persists the result as one transaction, on the same terms
     * as {@link #creditCoins(long)}: the record is only updated once the write succeeded, and a
     * cost the balance cannot cover is refused outright rather than driving it negative.
     *
     * @param cost coins to spend; must be positive and must be covered
     * @return true when the cost was paid and the record committed
     */
    boolean spendCoins(long cost) {
        long current = coinBalance();
        if (!CoinBalance.canSpend(current, cost)) return false;
        return commitCoin(CoinBalance.spend(current, cost));
    }

    /**
     * Writes a new balance and only then adopts it.
     *
     * <p>The candidate record is built in a separate array, so a failed write cannot leave the
     * live record partly updated. Character completion bytes and the settings byte are copied in
     * unchanged; only the coin bytes differ.
     */
    private boolean commitCoin(long next) {
        if (loadState == LOAD_FAILED) return false;
        byte[] candidate = new byte[DATA_LEN];
        System.arraycopy(data, 0, candidate, 0, DATA_LEN);
        writeCoin(candidate, COIN_INDEX, next);
        if (!storage.write(RECORD_ID, candidate)) return false;
        System.arraycopy(candidate, 0, data, 0, DATA_LEN);
        return true;
    }

    /**
     * Decodes a big-endian 64-bit balance. Pure byte[] arithmetic, matching the style of
     * {@link #transposeLegacyRecord(byte[])}, so it is testable without any storage at all.
     *
     * @param record source bytes; indices past the end read as zero, so a record truncated from the
     *        low end keeps its leading bytes and loses its least significant ones
     * @param offset first byte of the encoded value
     * @return the decoded value, which may be negative if the stored bytes say so
     */
    static long readCoin(byte[] record, int offset) {
        if (record == null) return 0;
        long value = 0;
        for (int i = 0; i < COIN_LEN; i++) {
            int index = offset + i;
            long b = index >= 0 && index < record.length ? (record[index] & 0xffL) : 0L;
            value = (value << 8) | b;
        }
        return value;
    }

    /** Encodes a balance big-endian into eight bytes. Bytes outside the range are left untouched. */
    static void writeCoin(byte[] record, int offset, long value) {
        if (record == null) return;
        for (int i = COIN_LEN - 1; i >= 0; i--) {
            int index = offset + i;
            if (index >= 0 && index < record.length) record[index] = (byte) (value & 0xffL);
            value >>= 8;
        }
    }

    private void load() {
        byte[] loaded;
        try {
            loaded = storage.read(RECORD_ID);
        } catch (StorageFailure failure) {
            // The stored progress is unknown. Keep the zeroed defaults in memory but refuse every
            // later write, so a transient read error can never overwrite the record it hid.
            loadState = LOAD_FAILED;
            return;
        }
        if (loaded == null) {
            loadState = LOAD_ABSENT;
            return;
        }
        if (loaded.length == LEGACY_LEN) {
            // Legacy records are stage-major (data[stage] bit character); this build is
            // character-major (data[character] bit stage). Interpret, never copy.
            byte[] migrated = transposeLegacyRecord(loaded);
            for (int i = 0; i < DATA_LEN; i++) data[i] = migrated[i];
        } else {
            int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
            for (int i = 0; i < count; i++) data[i] = loaded[i];
        }
        loadState = LOAD_LOADED;
    }

    /**
     * Transposes a 6-byte legacy record into the 48-byte character-major layout used here.
     *
     * <p>Verified against the reference JAR bytecode: its {@code isCompleted} reads
     * {@code data[stageOrder]} and tests {@code (1 << characterIndex)}, so legacy byte
     * {@code s} bit {@code c} means "character c completed stage s". This build reads
     * {@code data[characterIndex]} and tests {@code (1 << stageOrder)}, so the same fact must
     * land in byte {@code c} at bit {@code s}.
     *
     * <p>Settings move verbatim from legacy index 5 to index 47: both layouts keep hardcore in
     * bit 0, and the legacy writer only ever set the character index in bits 0-3, so the 6-bit
     * field used here is a superset. Bits 5-7 of legacy stage bytes would describe characters
     * that do not exist in this 5-character roster and have no destination, so they are dropped.
     *
     * <p>A 6-byte length alone proves nothing about validity; this only re-interprets a known
     * layout. The caller decides whether to trust the result.
     *
     * @param legacy exactly {@link #LEGACY_LEN} bytes
     * @return a new {@link #DATA_LEN}-byte array; the input is never modified
     */
    static byte[] transposeLegacyRecord(byte[] legacy) {
        if (legacy == null || legacy.length != LEGACY_LEN) {
            throw new IllegalArgumentException("legacy record must be exactly " + LEGACY_LEN + " bytes");
        }
        byte[] current = new byte[DATA_LEN];
        for (int stage = 0; stage < LEGACY_STAGES; stage++) {
            int stageMask = legacy[stage] & 0xff;
            for (int character = 0; character < LEGACY_CHARS; character++) {
                if ((stageMask & (1 << character)) != 0) current[character] |= (1 << stage);
            }
        }
        current[SETTINGS_INDEX] = legacy[LEGACY_SETTINGS_INDEX];
        return current;
    }

    /**
     * Commits the live record.
     *
     * <p>Now returns a result instead of swallowing the outcome, so a caller that needs to know
     * whether its change survived can check it. Existing callers deliberately ignore it, which
     * keeps their previous best-effort behaviour unchanged.
     *
     * @return true when the record was written, false when storage refused or failed
     */
    private boolean save() {
        // Refuse every write while the record is unknown. Without this guard a single read error
        // would let the next ordinary mutation persist the empty defaults over real progress.
        if (loadState == LOAD_FAILED) return false;
        return storage.write(RECORD_ID, data);
    }

    /** The production seam: the one and only place that touches {@code javax.microedition.rms}. */
    static final class RmsRecordStorage implements RecordStorage {
        private final RecordStoreOpener opener;

        RmsRecordStorage() {
            this(new RealRecordStoreOpener());
        }

        /**
         * Test seam. The opener is the only substitutable part: a real store cannot be built off
         * device, but every failure mode this adapter distinguishes is raised while opening or
         * while reading, and opening is what a test can drive.
         */
        RmsRecordStorage(RecordStoreOpener opener) {
            this.opener = opener == null ? new RealRecordStoreOpener() : opener;
        }

        public byte[] read(int recordId) {
            RecordStore store = null;
            try {
                store = opener.open(STORE, false);
                return store.getRecord(recordId);
            } catch (RecordStoreException problem) {
                // One catch, then classify. The two absence types are siblings under
                // RecordStoreException, so catching them by name could silently reorder and let
                // the generic clause swallow them.
                if (isAbsence(problem)) return null;
                throw new StorageFailure("record read failed");
            } finally {
                close(store);
            }
        }

        public boolean write(int recordId, byte[] payload) {
            RecordStore store = null;
            boolean written = false;
            boolean closed = true;
            try {
                store = opener.open(STORE, true);
                store.setRecord(recordId, payload, 0, payload.length);
                // This pinned API stub exposes no commit operation, so there is nothing to call
                // after setRecord. That is not a durability guarantee: the contract below is only
                // as strong as closeRecordStore() being reached and succeeding.
                written = true;
            } catch (RecordStoreException failure) {
                written = false;
            } finally {
                // Close still runs when the open succeeded, and its outcome decides the result.
                // Returning from the try block would fix the answer before this ran.
                closed = close(store);
            }
            return written && closed;
        }

        /** Closes the store. A store that was never opened needs no closing and is not a failure. */
        private static boolean close(RecordStore store) {
            if (store == null) return true;
            try {
                store.closeRecordStore();
                return true;
            } catch (RecordStoreException failure) {
                return false;
            }
        }
    }
}
