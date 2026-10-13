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
                        for (int i = 0; i < 5; i++) data[i] = loaded[i];
                        data[SETTINGS_INDEX] = loaded[5];
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
