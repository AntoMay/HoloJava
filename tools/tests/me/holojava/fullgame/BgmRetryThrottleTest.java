package me.holojava.fullgame;

/**
 * Pins the retry throttle in {@link BgmAudio}: the menu theme is requested on every tick of the
 * run loop, so on a device whose MMAPI refuses to build the player (or a jar missing the file)
 * the old code re-read the whole MIDI and re-ran createPlayer thirty times a second, forever.
 *
 * <p>The window is plain arithmetic wrapped around the media calls, so it can be pinned
 * headlessly: in the test classpath the track resource does not resolve, the attempt fails
 * before {@code Manager.createPlayer}, and no media call is made — exactly what BgmAudio's
 * class javadoc promises for off-device use. Time is never slept through: {@code retryAfterMs}
 * is rewound to simulate a lapse instead.
 */
public final class BgmRetryThrottleTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        testOneAttemptPerWindowRatherThanPerTick();
        testTheFailedTrackIsRetriedAfterTheWindowLapses();
        testADifferentTrackIsNeverBlocked();
        testDisabledAudioAndUnmappedStagesLoadNothing();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- 1. Ten ticks cost one attempt, not ten ---------------------------------

    private static void testOneAttemptPerWindowRatherThanPerTick() {
        System.out.println("[1] a failing track is attempted once per window");
        BgmAudio bgm = new BgmAudio();
        for (int i = 0; i < 10; i++) bgm.playMenu();
        expectEq(bgm.loadAttempts, 1, "ten ticks attempted the load once");
        expect(bgm.retryAfterMs > System.currentTimeMillis(), "the retry window is open");
        expect(BgmAudio.MENU_MID.equals(bgm.lastFailedPath), "the menu track is the failed one");

        bgm.playMenu();
        expectEq(bgm.loadAttempts, 1, "an attempt inside the window is skipped");
    }

    // ---- 2. The window lapsing restores the track -------------------------------

    private static void testTheFailedTrackIsRetriedAfterTheWindowLapses() {
        System.out.println("[2] the track is retried after the window lapses");
        BgmAudio bgm = new BgmAudio();
        bgm.playMenu();
        expectEq(bgm.loadAttempts, 1, "the first attempt ran");
        bgm.playMenu();
        expectEq(bgm.loadAttempts, 1, "the second tick was held back");

        bgm.retryAfterMs = 0; // the window lapses without sleeping through five seconds
        bgm.playMenu();
        expectEq(bgm.loadAttempts, 2, "the retry ran once the window was open");
        expect(bgm.retryAfterMs > System.currentTimeMillis(),
            "and a failure inside the retry reopens it");
    }

    // ---- 3. Another track is never held up by this one's failure ----------------

    private static void testADifferentTrackIsNeverBlocked() {
        System.out.println("[3] a different track is not blocked by the failure");
        BgmAudio bgm = new BgmAudio();
        bgm.playMenu();                       // fails headlessly and opens the window
        expectEq(bgm.loadAttempts, 1, "the menu track was attempted");

        bgm.play(0);                          // a different path: a deliberate change, must run
        expectEq(bgm.loadAttempts, 2, "the stage track is attempted at once");
        bgm.play(0);
        expectEq(bgm.loadAttempts, 2, "its own retry window does apply to it");

        // The failure on record is now the stage track's, so the menu track runs immediately:
        // the throttle remembers exactly one failure, and switching tracks never waits on a
        // stale one.
        bgm.playMenu();
        expectEq(bgm.loadAttempts, 3, "switching back to the menu track runs at once");
    }

    // ---- 4. Nothing is attempted when nothing should play -----------------------

    private static void testDisabledAudioAndUnmappedStagesLoadNothing() {
        System.out.println("[4] disabled audio and unmapped stages load nothing");
        BgmAudio bgm = new BgmAudio();
        bgm.playMenu();
        expectEq(bgm.loadAttempts, 1, "enabled audio attempted once");

        bgm.setEnabled(false);
        bgm.playMenu();
        bgm.play(0);
        expectEq(bgm.loadAttempts, 1, "disabled audio never reaches the loader");

        bgm.setEnabled(true);
        bgm.play(999);                        // musicFor(999) is null: silence, no load
        expectEq(bgm.loadAttempts, 1, "an unmapped stage loads nothing");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
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
