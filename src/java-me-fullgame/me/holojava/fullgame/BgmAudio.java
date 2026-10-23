package me.holojava.fullgame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;

/**
 * Music behind the game: a looping MIDI BGM loaded from the JAR — the menu theme (Shiny Smily
 * Story) on the pre-game screens, the stage track during a run.
 *
 * <p>The tracks are transcriptions of the unpacked source OGGs (basic-pitch pipeline, documented
 * in docs/PROJECT_STATUS.md): each source file weighs 5-15 MB, which no J2ME jar can carry,
 * while the transcribed MIDI weighs tens of KB — sized by the owner's 2026-10-18 policy (cap
 * 60 KB per track). Stage mapping is by stage index, and Arena Hutan Sawit (stage6, the
 * project's original stage) is deliberately silent: it has no source track and was requested
 * without music.
 *
 * <p>Every MMAPI touch is wrapped in catch (Throwable): a device without MMAPI, a corrupt
 * stream, or a decode failure must degrade to silence, never to a crash. The class makes no
 * media call until the first {@link #playMenu()} or {@link #play(int)}, so headless tests can
 * construct the canvas and drive the UI without ever touching the media stack.
 */
final class BgmAudio {

    private Player player;
    private String current;
    private boolean enabled = true;

    /**
     * Minimum gap between attempts to (re)load a track that failed. The menu theme is requested on
     * every tick of the run loop, so on a device whose MMAPI refuses to build the player (or a jar
     * missing the file) the old code re-read the whole MIDI and re-ran createPlayer thirty times a
     * second, forever. Within this window the failed track is not attempted at all; once it
     * lapses, one retry runs and the next failure reopens it, so a transient media failure costs
     * at most this much silence instead of a permanent retry storm.
     */
    private static final long RETRY_INTERVAL_MS = 5000;

    /**
     * The track whose last attempt failed, or null when none has. Package-visible because
     * {@code BgmRetryThrottleTest} drives the throttle headlessly: only the media calls
     * themselves are device-only, the retry decision around them is plain arithmetic.
     */
    String lastFailedPath;

    /** Clock time before which {@link #lastFailedPath} must not be attempted again. The test
     *  rewinds this field to simulate a lapse instead of sleeping through the window. */
    long retryAfterMs;

    /** Load attempts that actually started, counted after the throttle. The test counts these to
     *  prove one attempt per window rather than one per tick. */
    int loadAttempts;

    /**
     * Pengaturan's Audio row: false turns {@link #play(int)} into a no-op and immediately stops
     * anything playing, so flipping the row silences the game at once. The persisted copy lives
     * in SaveStore; the canvas pushes it here on every toggle and before every play.
     */
    void setEnabled(boolean value) {
        enabled = value;
        if (!value) stop();
    }

    /**
     * MIDI resource for a stage index, or null for a stage that plays no music.
     *
     * <p>The one place the stage-to-track table lives; swapping a track is a one-line change
     * here. The source OGGs are in HoloCure_Unpacked/sounds/bgm, the MIDI siblings in
     * assets/fullgame/audio.
     */
    static String musicFor(int stageIndex) {
        switch (stageIndex) {
            case 0:  return "/assets/fullgame/audio/bgm_suspect.mid";          // Grassy Plains
            case 1:  return "/assets/fullgame/audio/bgm_candygoround.mid";     // Holo Office
            case 2:  return "/assets/fullgame/audio/bgm_halloweennight.mid";   // Halloween Castle
            case 3:  return "/assets/fullgame/audio/bgm_identity.mid";         // Gelora Bung Yagoo
            case 4:  return "/assets/fullgame/audio/bgm_fantasykiseki.mid";    // Fantasy Island
            case 5:  return "/assets/fullgame/audio/bgm_adtruck.mid";          // Grassy Plains (Night)
            case 6:  return "/assets/fullgame/audio/bgm_stardust.mid";         // Holo Office (Evening)
            case 7:  return "/assets/fullgame/audio/bgm_mythortreat.mid";      // Halloween Castle (Myth)
            case 8:  return "/assets/fullgame/audio/bgm_bokuragakurukara.mid"; // Gelora Bung Yagoo (Night)
            case 9:  return "/assets/fullgame/audio/bgm_concert.mid";          // Concert Stage (Time)
            case 10:  return null;      // Arena Hutan Sawit (custom) - no BGM
            default: return null;
        }
    }

    /**
     * The title-and-menu theme: a transcription of bgm_SSS.ogg (Shiny Smily Story), the original
     * game's menu BGM. It loops on every pre-game screen — splash, Main Menu and every sub-menu
     * — and hands over to the stage track the moment a run starts.
     */
    static final String MENU_MID = "/assets/fullgame/audio/bgm_SSS.mid";

    /** Starts the stage's track, looping forever. No-op if disabled, if that track already plays,
     *  or if its last attempt failed inside the retry window ({@link #RETRY_INTERVAL_MS}). */
    void play(int stageIndex) {
        play(musicFor(stageIndex));
    }

    /** Starts the menu theme, looping forever. No-op under the same three rules as
     *  {@link #play(int)} — and this one is asked for on every tick, which is exactly why the
     *  retry window exists. */
    void playMenu() {
        play(MENU_MID);
    }

    private void play(String path) {
        if (!enabled) { stop(); return; }
        if (path == null) { stop(); return; }
        if (path.equals(current) && player != null) return;
        // A track that just failed is held back until the window lapses. Only that exact track is
        // held back: switching to another track is a deliberate change, so it always runs, and a
        // stale failure can never keep a different track from starting.
        if (path.equals(lastFailedPath) && System.currentTimeMillis() < retryAfterMs) return;
        loadAttempts++;
        stop();
        InputStream raw = null;
        try {
            raw = BgmAudio.class.getResourceAsStream(path);
            if (raw == null) { markFailed(path); return; } // asset missing: silence, never a crash
            player = Manager.createPlayer(new ByteArrayInputStream(readAll(raw)), "audio/midi");
            // MMAPI's Player.LOOP_CONTINUOUSLY is the spec constant -1; the pinned microemu
            // API stub omits the constant (it has setLoopCount itself), so the literal is used.
            player.setLoopCount(-1);
            player.prefetch();
            player.start();
            current = path;
            lastFailedPath = null;
        } catch (Throwable t) {
            stop(); // release any half-made player; this device plays on silently
            markFailed(path);
        } finally {
            try { if (raw != null) raw.close(); } catch (IOException ignored) { }
        }
    }

    /** Opens the retry window for a track whose attempt just failed. */
    private void markFailed(String path) {
        lastFailedPath = path;
        retryAfterMs = System.currentTimeMillis() + RETRY_INTERVAL_MS;
    }

    /** Stops and releases the current track, if any. Safe to call when nothing plays. */
    void stop() {
        if (player == null) return;
        try { player.close(); } catch (Throwable ignored) { }
        player = null;
        current = null;
    }

    /** Pauses the current track without releasing it. Safe to call when nothing plays. */
    void pause() {
        if (player == null) return;
        try { player.stop(); } catch (Throwable ignored) { }
    }

    /** Resumes a paused track. Safe to call when nothing is paused. */
    void resume() {
        if (player == null) return;
        try { player.start(); } catch (Throwable ignored) { }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
