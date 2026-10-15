package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotFoundException;
import javax.microedition.rms.RecordStoreNotOpenException;

/**
 * Exercises the one code path the fake-storage tests cannot reach: how
 * {@link SaveStore.RmsRecordStorage} maps a real {@code javax.microedition.rms} failure onto either
 * "absent" or "unknown".
 *
 * <p><b>Why this file exists.</b> The coin-persistence tests drive {@link SaveStore.RecordStorage}
 * with an in-memory fake, which replaces the RMS adapter outright. The adapter's exception
 * classification was therefore never executed by any test, and a suite-wide negative control
 * confirmed it: reverting the absence fix left all tests green. That is the gap closed here.
 *
 * <p><b>How real exceptions are obtained.</b> The pinned MIDP stub throws
 * {@code Error("API Stub has been used")} from every constructor, so no RMS exception can be
 * created with {@code new}. This test allocates them without running a constructor, via
 * {@code sun.misc.Unsafe.allocateInstance}, reached purely reflectively so the file still compiles
 * under {@code --release 8}. The instances are genuine {@code javax.microedition.rms} objects, so
 * the {@code instanceof} matching in production is exercised for real rather than simulated.
 *
 * <p>This is test-only tooling: nothing here is compiled into the game JAR, and no game source
 * file references {@code sun.*} or reflection beyond the standard API.
 *
 * <p><b>Still not proven.</b> That a real device raises {@code RecordStoreNotFoundException} when
 * {@code openRecordStore(name, false)} finds no store, and that {@code setRecord} without an
 * explicit commit is durable. Both need an emulator or a handset.
 */
public final class SaveStoreRmsPathTest {

    private static int checks = 0;
    private static int failures = 0;

    /** Allocates a throwable of the given RMS class without invoking its stub constructor. */
    private static RecordStoreException raw(String simpleName) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
            Class<?> target = Class.forName("javax.microedition.rms." + simpleName);
            return (RecordStoreException) allocate.invoke(unsafe, target);
        } catch (Throwable t) {
            throw new IllegalStateException(
                "cannot allocate " + simpleName + " without a constructor; the RMS stub refuses it"
                        + " and sun.misc.Unsafe is unavailable", t);
        }
    }

    /** An opener that always fails the same way, standing in for a device's open call. */
    private static SaveStore.RecordStoreOpener failingWith(final RecordStoreException failure) {
        return new SaveStore.RecordStoreOpener() {
            public RecordStore open(String name, boolean createIfMissing) throws RecordStoreException {
                throw failure;
            }
        };
    }

    /** An opener that records the arguments it was called with and then fails. */
    private static SaveStore.RecordStoreOpener recording(final String[] seen) {
        return new SaveStore.RecordStoreOpener() {
            public RecordStore open(String name, boolean createIfMissing) throws RecordStoreException {
                seen[0] = name;
                seen[1] = createIfMissing ? "create" : "nocreate";
                throw raw("RecordStoreNotFoundException");
            }
        };
    }

    public static void main(String[] args) {
        testAbsenceClassificationDirectly();
        testReadMapsAbsenceToNull();
        testReadMapsOtherFailureToStorageFailure();
        testWriteReportsFailureWhenOpenFails();
        testReadUsesCreateIfMissingFalseAndTheSameStoreName();
        testEndToEndNewInstallThroughTheRealAdapter();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- 1. The production predicate, against real RMS objects --------------------

    /**
     * {@code SaveStore.isAbsence} is the exact statement that regressed. These are real
     * {@code javax.microedition.rms} instances, so this asserts the shipped matching rule and not
     * a stand-in for it.
     */
    private static void testAbsenceClassificationDirectly() {
        System.out.println("[1] isAbsence against real RMS exceptions");
        expect(SaveStore.isAbsence(raw("RecordStoreNotFoundException")),
            "a store that does not exist is absence");
        expect(SaveStore.isAbsence(raw("InvalidRecordIDException")),
            "a record that does not exist is absence");

        expect(!SaveStore.isAbsence(raw("RecordStoreException")),
            "a plain RecordStoreException is not absence");
        expect(!SaveStore.isAbsence(raw("RecordStoreNotOpenException")),
            "a closed store is not absence");
        expect(!SaveStore.isAbsence(raw("RecordStoreFullException")),
            "a full store is not absence");

        // The hierarchy must not let one absence type masquerade as the other, which would make
        // the two indistinguishable no matter how the predicate is written.
        expect(RecordStoreNotFoundException.class.isAssignableFrom(InvalidRecordIDException.class) == false
            && InvalidRecordIDException.class.isAssignableFrom(RecordStoreNotFoundException.class) == false,
            "the two absence types remain distinct siblings");
    }

    // ---- 2. The regression itself: open fails because the store is missing --------

    /**
     * This is the S2.2 regression. {@code openRecordStore(name, false)} on a device with no store
     * raises {@code RecordStoreNotFoundException}; before the fix that became a hard read failure,
     * and a brand-new player could then never save anything again for the life of the process.
     */
    private static void testReadMapsAbsenceToNull() {
        System.out.println("[2] read maps both absence types to null");
        RecordStoreException storeMissing = raw("RecordStoreNotFoundException");
        RecordStoreException recordMissing = raw("InvalidRecordIDException");

        SaveStore.RecordStorage viaStore = new SaveStore.RmsRecordStorage(failingWith(storeMissing));
        SaveStore.RecordStorage viaRecord = new SaveStore.RmsRecordStorage(failingWith(recordMissing));

        // Caught rather than propagated, so a regression is reported as a failed check and the
        // remaining sections still run instead of the whole file aborting on the first one.
        expect(readsAsAbsent(viaStore), "a missing store reads as absent, not as a failure");
        expect(readsAsAbsent(viaRecord), "a missing record reads as absent, not as a failure");
    }

    /** True when the read returned null rather than raising {@link SaveStore.StorageFailure}. */
    private static boolean readsAsAbsent(SaveStore.RecordStorage storage) {
        try {
            return storage.read(1) == null;
        } catch (SaveStore.StorageFailure wronglyTreatedAsFailure) {
            return false;
        }
    }

    // ---- 3. Every other failure must stay a failure ------------------------------

    private static void testReadMapsOtherFailureToStorageFailure() {
        System.out.println("[3] other failures stay failures");
        String[] others = { "RecordStoreException", "RecordStoreNotOpenException", "RecordStoreFullException" };
        for (int i = 0; i < others.length; i++) {
            SaveStore.RecordStorage storage =
                    new SaveStore.RmsRecordStorage(failingWith(raw(others[i])));
            boolean threw = false;
            try {
                storage.read(1);
            } catch (SaveStore.StorageFailure expected) {
                threw = true;
            }
            expect(threw, others[i] + " raises StorageFailure instead of looking absent");
        }
    }

    // ---- 4. A failed open must never look like a committed write -----------------

    private static void testWriteReportsFailureWhenOpenFails() {
        System.out.println("[4] write reports failure when opening fails");
        SaveStore.RecordStorage storage = new SaveStore.RmsRecordStorage(
                failingWith(raw("RecordStoreException")));
        expect(!storage.write(1, new byte[56]),
            "a write that never reached the store reports failure");
    }

    // ---- 5. The adapter must not have quietly changed its RMS contract -----------

    private static void testReadUsesCreateIfMissingFalseAndTheSameStoreName() {
        System.out.println("[5] read opens the established store without creating it");
        String[] seen = new String[2];
        SaveStore.RecordStorage storage = new SaveStore.RmsRecordStorage(recording(seen));
        expect(readsAsAbsent(storage), "the recording opener ran and its failure was treated as absent");
        expectEq(seen[0], "holojava_fullgame_v1", "the store name is unchanged");
        expectEq(seen[1], "nocreate", "reading must not create the store");
    }

    // ---- 6. A new install driven through the real adapter ------------------------

    /**
     * The user-visible consequence, driven through the real adapter rather than a fake.
     *
     * <p>A store that does not exist yet must let the player build their first save; a store that
     * fails for any other reason must leave them locked out rather than overwriting progress. Both
     * are decided by the adapter's classification, so both are asserted here.
     *
     * <p>Only the load half is exercised: a store that successfully opens cannot be faked, because
     * the stub's {@code getRecord} and {@code setRecord} are unreachable off-device. The committing
     * half of a fresh save is covered by the coin-persistence tests through {@code RecordStorage}.
     */
    private static void testEndToEndNewInstallThroughTheRealAdapter() {
        System.out.println("[6] new install through the real adapter");

        SaveStore fresh = new SaveStore(new SaveStore.RmsRecordStorage(
                failingWith(raw("RecordStoreNotFoundException"))));
        expectEq(fresh.loadState(), SaveStore.LOAD_ABSENT,
            "no store at all reads as a new install, so a first save is allowed");

        SaveStore broken = new SaveStore(new SaveStore.RmsRecordStorage(
                failingWith(raw("RecordStoreNotOpenException"))));
        expectEq(broken.loadState(), SaveStore.LOAD_FAILED,
            "any other storage error reads as unknown progress");
        expect(!broken.creditCoins(5L), "an unknown record still refuses writes");
        expectEq(broken.coinBalance(), 0L, "and reports the empty default rather than invented coins");

        expect(fresh.loadState() != broken.loadState(),
            "the two outcomes stay distinguishable through the real adapter");
    }

    // ---- helpers ----------------------------------------------------------------

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(long got, long want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(String got, String want, String label) {
        expect(got != null && got.equals(want), label + ": expected " + want + " got " + got);
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}