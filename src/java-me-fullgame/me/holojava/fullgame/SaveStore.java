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
    // bytes 48..55 are the permanent global coin balance, and byte 56 is the audio preference.
    // Growing DATA_LEN is the migration: load() copies min(record length, DATA_LEN), so older
    // shorter records load in full and the bytes they never had stay zero — a zero audio byte
    // decodes to Audio ON, which is the only behaviour those builds ever had. No store rename,
    // no reset.
    //
    // Completion is two bytes per character, both inside the 0..46 region that was already
    // reserved for it: byte i holds stage orders 0-7, byte HIGH_BASE + i holds orders 8-15.
    // The primary byte keeps its historical index, so saves written by earlier builds decode
    // unchanged and the high bytes simply read as zero. See HIGH_BASE and MAX_STAGE_ORDER.
    //
    // Upgrade levels live in 34 nibbles at bytes 10..26 (one per row of the three-tab shop)
    // with the layout version at byte 46, all inside that same reserved region and all within
    // DATA_LEN, so the record length never changes. Older records holding the eight-passive
    // global vector (layout v2) or the per-character vector before it (v1) are remapped by
    // migrateShopLayout() on load. See UPGRADE_BASE and LAYOUT_VERSION.
    private static final int DATA_LEN = 57;
    private static final int SETTINGS_INDEX = 47;
    private static final int COIN_INDEX = 48;
    private static final int AUDIO_INDEX = 56;
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
     * Coin writes are debounced. A pickup used to write the whole record to RMS the moment it was
     * collected, and a magnet can pull a dozen pickups within a couple of seconds — one flash
     * write each, mid-frame. The balance now advances in memory at once (gameplay never waits for
     * storage) and the disk catches up at most once per window: by the next credit after it, by
     * {@link #flush()}, or by any other mutation that writes the record anyway. The window is
     * also the bound on what a power cut can cost — the coins collected since the last write —
     * and {@link #flush()} runs on pause and on exit to close even that gap on the normal paths.
     */
    static final long COIN_WRITE_INTERVAL_MS = 3000;

    /** The balance changed in memory after the last successful write of the record. */
    private boolean coinDirty;

    /** Clock time of the last write attempt that carried the balance, successful or refused.
     *  Stamping failures too is what keeps a broken store from turning into its own write storm:
     *  it is retried once per window, not once per pickup. */
    private long coinWriteAtMs;

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

    // ---- upgrade levels (layout version 3) -------------------------------------------------
    // The shop is global, so the record carries ONE level vector for all 34 upgrade rows: one
    // nibble per row in ContentCatalog.UPGRADES order, 17 bytes at 10..26. Every row's cap is
    // at most 10, which a nibble (0..15) spans, and no nibble can straddle a byte boundary.
    // Two rows never use their slot: Hardcore mirrors the settings bit (so the Settings screen
    // and the shop's Others tab always show the same flag) and Refund All is an action with no
    // stored level; their nibbles simply stay zero.
    //
    // Bytes 27..45 stay reserved and zero; byte 46 carries LAYOUT_VERSION, which is what makes
    // older layouts detectable: a record written before this layout has 0 or 2 there, so load()
    // remaps whatever it holds via migrateShopLayout() and stamps 3, after which later loads
    // skip it entirely.
    private static final int UPGRADE_BASE = 10;
    private static final int UPGRADE_BITS = 4;
    private static final int UPGRADE_MASK = 0xF;
    // The v1 per-character vector spanned bytes 10..29; everything up to the reserved end is
    // cleared during a migration so stale nibbles from any older layout cannot survive.
    private static final int LEGACY_SHOP_END = 45;
    private static final int LAYOUT_VERSION_INDEX = 46;
    private static final int LAYOUT_VERSION = 3;
    private static final int LEGACY_PASSIVES = 8;

    /**
     * Where each of the eight legacy global passives lands in the new catalog. The old order
     * was body, might, guard, swift, precision, insight, fortune, tempo; the new rows are
     * grouped by shop tab, so guard (old index 2) now sits at Defense Up and tempo (old 7) at
     * Haste Up. Resolved by id rather than by hard-coded index so appending a row above these
     * can never silently redirect a migration.
     */
    private static final int[] LEGACY_PASSIVE_TARGET = {
        ContentCatalog.upgradeIndex("max_hp"),      // body (Vitality)
        ContentCatalog.upgradeIndex("atk_up"),      // might
        ContentCatalog.upgradeIndex("defense_up"),  // guard
        ContentCatalog.upgradeIndex("spd_up"),      // swift
        ContentCatalog.upgradeIndex("crit_up"),     // precision
        ContentCatalog.upgradeIndex("exp_up"),      // insight
        ContentCatalog.upgradeIndex("money_up"),    // fortune
        ContentCatalog.upgradeIndex("haste_up")     // tempo
    };

    /**
     * Index of the nibble holding the global level of one upgrade, or -1 when out of range.
     */
    private int upgradeNibble(int upgradeIndex) {
        if (upgradeIndex < 0 || upgradeIndex >= ContentCatalog.UPGRADES.length) return -1;
        return upgradeIndex;
    }

    private int nibbleAt(int nibble) {
        int index = UPGRADE_BASE + nibble / 2;
        int shift = (nibble & 1) * UPGRADE_BITS;
        return (data[index] & 0xff) >>> shift & UPGRADE_MASK;
    }

    private void writeNibble(int nibble, int level) {
        int index = UPGRADE_BASE + nibble / 2;
        int shift = (nibble & 1) * UPGRADE_BITS;
        int clearMask = ~(UPGRADE_MASK << shift) & 0xff;
        int packed = (data[index] & 0xff) & clearMask;
        data[index] = (byte) (packed | (level << shift));
    }

    /**
     * Purchased global level of one shop upgrade, 0..its own {@code maxLevel}.
     *
     * <p>Returns 0 for anything out of range rather than throwing: a missing level or a bad index
     * both mean "not bought yet", and a shop that refuses to render because a byte was corrupt
     * would be worse than one that shows level 0.
     */
    int upgradeLevel(int upgradeIndex) {
        int nibble = upgradeNibble(upgradeIndex);
        if (nibble < 0) return 0;
        if (UPGRADE_BASE + nibble / 2 >= LAYOUT_VERSION_INDEX) return 0;
        return nibbleAt(nibble);
    }

    /**
     * Records a purchased upgrade level globally and persists it as one transaction.
     *
     * <p>Refused outright: unknown indices, levels outside the row's own 0..maxLevel range, rows
     * that are locked (not for sale yet) and rows whose buyKind is not a levelled purchase
     * (Hardcore reads the settings bit, Refund All stores nothing). A stale price or a corrupt
     * read can therefore never write a level the shop would never sell back into a neighbouring
     * nibble of the same byte.
     */
    void setUpgradeLevel(int upgradeIndex, int level) {
        int nibble = upgradeNibble(upgradeIndex);
        if (nibble < 0) return;
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(upgradeIndex);
        if (def == null || def.locked || def.buyKind != ContentCatalog.BUY_LEVEL) return;
        if (level < 0 || level > def.maxLevel) return;
        if (UPGRADE_BASE + nibble / 2 >= LAYOUT_VERSION_INDEX) return;
        writeNibble(nibble, level);
        save();
    }

    /**
     * Total coins every level currently owned has cost so far, summed from the price tables.
     *
     * <p>This is the only refund arithmetic in the game: the shop never stores what was paid,
     * because the price of each level is public catalog data, so recomputing from it cannot drift
     * from what the same table would charge on the next purchase.
     */
    long refundValue() {
        long total = 0;
        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(i);
            if (def == null || def.prices == null) continue;
            int level = upgradeLevel(i);
            for (int l = 0; l < level && l < def.prices.length; l++) total += def.prices[l];
        }
        return total;
    }

    /**
     * Resets every upgrade to zero and credits the full refund, in one write.
     *
     * <p>One transaction, same rule as every other balance change here: the candidate record
     * carries both the zeroed levels and the credited balance, and the live record is only
     * updated after the write is reported successful. A refund that cannot be credited (balance
     * overflow) is refused as a whole: levels stay purchased and nothing is written. Only the
     * upgrade nibbles are cleared — completion, settings and the selected character are not
     * purchases and must survive the refund.
     *
     * @return true when the refund landed (including a no-op refund with nothing purchased)
     */
    boolean refundAll() {
        if (loadState == LOAD_FAILED) return false;
        long refund = refundValue();
        long current = coinBalance();
        if (refund > 0 && !CoinBalance.canAdd(current, refund)) return false;
        byte[] candidate = new byte[DATA_LEN];
        System.arraycopy(data, 0, candidate, 0, DATA_LEN);
        for (int i = UPGRADE_BASE; i <= UPGRADE_BASE + (ContentCatalog.UPGRADES.length - 1) / 2; i++) {
            candidate[i] = 0;
        }
        if (refund > 0) writeCoin(candidate, COIN_INDEX, CoinBalance.add(current, refund));
        if (!storage.write(RECORD_ID, candidate)) return false;
        System.arraycopy(candidate, 0, data, 0, DATA_LEN);
        return true;
    }

    /**
     * Remaps an older record's shop layout to version 3, once per record.
     *
     * <p>Layout v2 held eight global passive nibbles at bytes 10..13 (plus the long-gone Skill
     * Pasif flag at 14); anything older (v1) held a 5-character x 8-passive 0..10 vector in bytes
     * 10..29, from which the highest level any character had bought is kept — the player paid for
     * it, and the shop is global now. In both cases all eight legacy values are read into a
     * temporary first, then the whole reserved region is cleared, then each value is written to
     * its new row clamped to that row's new cap (the old cap was 5/10; new caps run 5..10). The
     * version byte is stamped last, so an interrupted or unmigrated record simply migrates again
     * on the next load instead of half-applying.
     */
    private void migrateShopLayout() {
        if ((data[LAYOUT_VERSION_INDEX] & 0xff) == LAYOUT_VERSION) return;
        boolean globalVector = (data[LAYOUT_VERSION_INDEX] & 0xff) >= 2;
        int[] old = new int[LEGACY_PASSIVES];
        if (globalVector) {
            for (int p = 0; p < LEGACY_PASSIVES; p++) old[p] = nibbleAt(p);
        } else {
            for (int p = 0; p < LEGACY_PASSIVES; p++) {
                int best = 0;
                for (int c = 0; c < ContentCatalog.CHARACTERS.length; c++) {
                    int level = nibbleAt(c * LEGACY_PASSIVES + p);
                    if (level > best) best = level;
                }
                old[p] = best;
            }
        }
        for (int i = UPGRADE_BASE; i <= LEGACY_SHOP_END; i++) data[i] = 0;
        for (int p = 0; p < LEGACY_PASSIVES; p++) {
            if (old[p] <= 0) continue;
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(LEGACY_PASSIVE_TARGET[p]);
            if (def == null) continue;
            writeNibble(LEGACY_PASSIVE_TARGET[p], old[p] > def.maxLevel ? def.maxLevel : old[p]);
        }
        data[LAYOUT_VERSION_INDEX] = (byte) LAYOUT_VERSION;
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

    /** Pengaturan's Audio row. Inverted on disk: bit 0 clear (0 — a fresh install or any
     *  pre-audio save) means ON, so music stays the default and no migration is needed. */
    boolean audioEnabled() { return (data[AUDIO_INDEX] & 1) == 0; }

    void setAudioEnabled(boolean value) {
        int v = data[AUDIO_INDEX] & 0xff;
        if (value) v &= ~1; else v |= 1;
        data[AUDIO_INDEX] = (byte) v;
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
     * Credits coins into the in-memory balance and schedules the record write.
     *
     * <p>The balance advances immediately (given a loadable record and a fitting amount):
     * persistence is optional, gameplay is not, so a pickup never waits on and never depends on a
     * flash write. Overflow is still rejected as a whole — if the amount does not fit, nothing is
     * credited and nothing is written, so a caller never receives a partial deposit. What changed
     * from the old write-first transaction is <em>when</em> the disk catches up: the write is
     * debounced per {@link #COIN_WRITE_INTERVAL_MS}, a refused write leaves the coins in memory
     * for the next attempt instead of costing them, and {@link #flush()} forces it out early.
     *
     * @param amount coins to credit; must be positive and must fit
     * @return true when the balance was credited in memory; false when the record is unusable
     *         (a failed load must never be overwritten) or the amount would overflow
     */
    boolean creditCoins(long amount) {
        if (loadState == LOAD_FAILED) return false;
        long current = coinBalance();
        if (!CoinBalance.canAdd(current, amount)) return false;
        writeCoin(data, COIN_INDEX, CoinBalance.add(current, amount));
        coinDirty = true;
        maybeWriteCoins();
        return true;
    }

    /**
     * Writes the record when the debounce window has elapsed; otherwise lets the credit wait.
     *
     * <p>Called from {@link #creditCoins} only. The window is measured from the last attempt,
     * successful or refused, so both a burst of pickups and a store that keeps failing produce at
     * most one write per window.
     */
    private void maybeWriteCoins() {
        if (System.currentTimeMillis() - coinWriteAtMs < COIN_WRITE_INTERVAL_MS) return;
        save();
    }

    /**
     * Pushes a debounced coin credit to storage now instead of waiting out the window.
     *
     * <p>Called where a delay must not survive: when the app is paused or stopped, and after the
     * one-time stage-clear reward, so a session's final coins and a run's biggest grant never
     * depend on where the window happened to stand.
     *
     * @return true when nothing was pending or the write committed; false when storage refused
     *         (the credit stays in memory for the next attempt)
     */
    boolean flush() {
        if (!coinDirty) return true;
        return save();
    }

    /**
     * Pays coins out of the balance, writing first. Spending is rare — a shop, not a pickup
     * stream — so it keeps the old transactional rule: the record is only updated once the write
     * succeeded, and a cost the balance cannot cover is refused outright rather than driving it
     * negative. Because the candidate record is built from the live one, a successful spend also
     * lands every debounced credit still waiting in memory, in that same single write.
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
     * Writes a new balance and only then adopts it. The spend path; {@link #creditCoins} no
     * longer comes through here.
     *
     * <p>The candidate record is built in a separate array, so a failed write cannot leave the
     * live record partly updated. Character completion bytes and the settings byte are copied in
     * unchanged; only the coin bytes differ. A successful write also carries any debounced
     * credits that were already sitting in {@code data}, so it marks the balance clean.
     */
    private boolean commitCoin(long next) {
        if (loadState == LOAD_FAILED) return false;
        byte[] candidate = new byte[DATA_LEN];
        System.arraycopy(data, 0, candidate, 0, DATA_LEN);
        writeCoin(candidate, COIN_INDEX, next);
        coinWriteAtMs = System.currentTimeMillis();
        if (!storage.write(RECORD_ID, candidate)) return false;
        System.arraycopy(candidate, 0, data, 0, DATA_LEN);
        coinDirty = false;
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
        migrateShopLayout();
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
        // Every write of the record carries the balance with it: on success the debounced credit
        // is now durable, on refusal it stays pending — and the attempt is stamped either way so
        // the retry lands inside the debounce window instead of right after this one.
        coinWriteAtMs = System.currentTimeMillis();
        if (!storage.write(RECORD_ID, data)) return false;
        coinDirty = false;
        return true;
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
