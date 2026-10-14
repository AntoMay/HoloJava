package me.holojava.fullgame;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotFoundException;

/** Optional RMS persistence. Run gameplay never depends on persistence succeeding. */
final class SaveStore {
    private static final String STORE = "holojava_fullgame_v1";
    private static final int RECORD_ID = 1;
    private static final int DATA_LEN = 48;
    private static final int SETTINGS_INDEX = 47;
    private static final int LEGACY_LEN = 6;
    private static final int LEGACY_STAGES = 5;   // legacy isCompleted required stageOrder < 5.
    private static final int LEGACY_CHARS = 5;    // legacy roster had 5 characters (bits 0-4).
    private static final int LEGACY_SETTINGS_INDEX = 5;

    private final byte[] data = new byte[DATA_LEN];

    SaveStore() {
        for (int i = 0; i < DATA_LEN; i++) data[i] = 0;
        load();
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

    private void load() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE, false);
            try {
                byte[] loaded = store.getRecord(RECORD_ID);
                if (loaded != null) {
                    if (loaded.length == LEGACY_LEN) {
                        // Legacy records are stage-major (data[stage] bit character); this build is
                        // character-major (data[character] bit stage). Interpret, never copy.
                        byte[] migrated = transposeLegacyRecord(loaded);
                        for (int i = 0; i < DATA_LEN; i++) data[i] = migrated[i];
                    } else {
                        int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
                        for (int i = 0; i < count; i++) data[i] = loaded[i];
                    }
                }
            } catch (RecordStoreException ignored) { }
        } catch (RecordStoreException ignored) {
            // Defaults remain valid when RMS is unavailable.
        } finally {
            if (store != null) {
                try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
            }
        }
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

    private void save() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE, true);
            try { store.setRecord(RECORD_ID, data, 0, data.length); }
            catch (RecordStoreException ignored) { }
            // This pinned API stub exposes no commit operation; record writes are immediate.
        } catch (RecordStoreException ignored) {
            // Persistence is optional.
        } finally {
            if (store != null) {
                try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
            }
        }
    }
}
