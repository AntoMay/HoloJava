package me.holojava.fullgame;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

/** Optional RMS persistence. Run gameplay never depends on persistence succeeding. */
final class SaveStore {
    private static final String STORE = "holojava_fullgame_v1";
    private static final int RECORD_ID = 1;
    // Bytes 0..46 are the per-character completion map, byte 47 is settings,
    // and bytes 48..55 are the permanent global coin balance. Growing DATA_LEN is the migration:
    // load() copies min(record length, DATA_LEN), so older shorter records load in full and the
    // bytes they never had stay zero, which decodes to a balance of 0. No store rename, no reset.
    //
    // Completion is two bytes per character, both inside the 0..46 region that was already
    // reserved for it: byte i holds stage orders 0-7, byte HIGH_BASE + i holds orders 8-15.
    // The primary byte keeps its historical index, so saves written by earlier builds decode
    // unchanged and the high bytes simply read as zero. See HIGH_BASE and MAX_STAGE_ORDER.
    //
    // Shop passive levels live in bytes 10..29, still inside that same reserved region and still
    // within DATA_LEN, so the record length and every occupied byte are unchanged. See PASSIVE_BASE.
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

    /**
     * Completion bits per character. Stage order 0-7 lives in the primary byte, exactly as the
     * existing on-device layout does, so every save written by an earlier build still decodes
     * unchanged. Orders 8-15 (added by the Arena Hutan Sawit stage, order 10) live in a second
     * byte per character in the still-unused region below the settings byte, so no previously
     * occupied byte moved and no new record length is needed.
     */
    private static final int MAX_STAGE_ORDER = 15;
    private static final int ORDER_BITS_LOW = 8;
    private static final int HIGH_BASE = 5; // high-order byte for character i lives at HIGH_BASE + i

    // ---- shop passive levels ---------------------------------------------------
    // Bytes 10..46 were reserved for the completion map and have never been occupied by anything:
    // completion uses 10 of them (5 primary + 5 high-order). The shop passives take bytes 10..29,
    // so the record keeps its historical length and no save has to migrate.
    //
    // Levels are stored per character rather than globally: each roster entry carries its own
    // 0..PASSIVE_MAX_LEVEL vector, so buying a passive for one character leaves the others at
    // zero. Five characters x eight passives is 40 nibbles, i.e. 20 bytes, laid out character-major
    // so one character's whole vector occupies four consecutive bytes. Nibbles rather than tighter
    // 3-bit fields because a 3-bit field straddles byte boundaries and a single-byte accessor
    // would silently lose the bits that spilled into the next byte; a nibble holds 0..15, which
    // spans the catalog cap of 10 with room to spare, and can never straddle.
    private static final int PASSIVE_BASE = 10;
    private static final int PASSIVE_BITS = 4;
    private static final int PASSIVE_MASK = 0xF;

    /**
     * Index of the nibble holding one character's level in one passive, or -1 when either index
     * is out of range.
     */
    private int passiveNibble(int characterIndex, int passiveIndex) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length) return -1;
        if (passiveIndex < 0 || passiveIndex >= ContentCatalog.PASSIVES.length) return -1;
        return characterIndex * ContentCatalog.PASSIVES.length + passiveIndex;
    }

    /**
     * Purchased level of one shop passive for one character, 0..{@link ContentCatalog#PASSIVE_MAX_LEVEL}.
     *
     * <p>Returns 0 for anything out of range rather than throwing: a missing level or a bad index
     * both mean "not bought yet", and a shop that refuses to render because a byte was corrupt
     * would be worse than one that shows level 0.
     */
    int passiveLevel(int characterIndex, int passiveIndex) {
        int nibble = passiveNibble(characterIndex, passiveIndex);
        if (nibble < 0) return 0;
        int index = PASSIVE_BASE + nibble / 2;
        if (index >= SETTINGS_INDEX) return 0;
        int shift = (nibble & 1) * PASSIVE_BITS;
        return (data[index] & 0xff) >>> shift & PASSIVE_MASK;
    }

    /**
     * Records a purchased passive level for one character and persists it as one transaction.
     *
     * <p>Levels outside 0..{@link ContentCatalog#PASSIVE_MAX_LEVEL} and unknown indices are
     * refused, so a stale price or a corrupt read cannot write a level the shop would never sell
     * back into a neighbouring nibble of the same byte.
     */
    void setPassiveLevel(int characterIndex, int passiveIndex, int level) {
        int nibble = passiveNibble(characterIndex, passiveIndex);
        if (nibble < 0) return;
        if (level < 0 || level > ContentCatalog.PASSIVE_MAX_LEVEL) return;
        int index = PASSIVE_BASE + nibble / 2;
        if (index >= SETTINGS_INDEX) return;
        int shift = (nibble & 1) * PASSIVE_BITS;
        int clearMask = ~(PASSIVE_MASK << shift) & 0xff;
        int packed = (data[index] & 0xff) & clearMask;
        data[index] = (byte) (packed | (level << shift));
        save();
    }

    boolean isCompleted(int characterIndex, int stageOrder) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length) return false;
        if (stageOrder < 0 || stageOrder > MAX_STAGE_ORDER) return false;
        int index = stageOrder < ORDER_BITS_LOW ? characterIndex : HIGH_BASE + characterIndex;
        int v = data[index] & 0xff;
        int bit = stageOrder < ORDER_BITS_LOW ? stageOrder : stageOrder - ORDER_BITS_LOW;
        return (v & (1 << bit)) != 0;
    }

    void setCompleted(int characterIndex, int stageOrder) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length) return;
        if (stageOrder < 0 || stageOrder > MAX_STAGE_ORDER) return;
        int index = stageOrder < ORDER_BITS_LOW ? characterIndex : HIGH_BASE + characterIndex;
        int v = data[index] & 0xff;
        int bit = stageOrder < ORDER_BITS_LOW ? stageOrder : stageOrder - ORDER_BITS_LOW;
        data[index] = (byte) (v | (1 << bit));
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
     * <p>Only orders 0-4 exist in the legacy layout, so this writes primary bytes only and leaves
     * every high-order byte at its zero default.
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
                // setRecord only replaces an existing record: the MIDP specification has it throw
                // InvalidRecordIDException for an id the store has never handed out, and
                // createRecord is the method that establishes one. This adapter used to call
                // setRecord on its own, so on a fresh install every write failed and nothing at all
                // was ever stored, not just the coin balance: stage unlocks, the selected character
                // and shop passives all went the same way.
                //
                // Falling back to addRecord keeps this portable. addRecord is core MIDP 1.0, so it
                // works on runtimes that lack createRecord, and on a fresh store it is assigned the
                // first free id, which is the record id this class already reads back.
                try {
                    store.setRecord(recordId, payload, 0, payload.length);
                    written = true;
                } catch (InvalidRecordIDException missing) {
                    int assigned = store.addRecord(payload, 0, payload.length);
                    written = assigned == recordId;
                }
                // This pinned API stub exposes no commit operation, so there is nothing to call
                // after setRecord. That is not a durability guarantee: the contract below is only
                // as strong as closeRecordStore() being reached and succeeding.
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
