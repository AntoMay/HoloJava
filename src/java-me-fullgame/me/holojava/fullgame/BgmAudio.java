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
            case 9:  return "/assets/fullgame/audio/bgm_concert.mid";          // Concert Stage
            default: return null; // stage6 Arena Hutan Sawit: silent by request
        }
    }

    /**
     * The title-and-menu theme: a transcription of bgm_SSS.ogg (Shiny Smily Story), the original
     * game's menu BGM. It loops on every pre-game screen — splash, Main Menu and every sub-menu
     * — and hands over to the stage track the moment a run starts.
     */
    static final String MENU_MID = "/assets/fullgame/audio/bgm_SSS.mid";

    /** Starts the stage's track, looping forever. No-op if disabled or if that track already plays. */
    void play(int stageIndex) {
        play(musicFor(stageIndex));
    }

    /** Starts the menu theme, looping forever. No-op if disabled or if it already plays. */
    void playMenu() {
        play(MENU_MID);
    }

    private void play(String path) {
        if (!enabled) { stop(); return; }
        if (path == null) { stop(); return; }
        if (path.equals(current) && player != null) return;
        stop();
        InputStream raw = null;
        try {
            raw = BgmAudio.class.getResourceAsStream(path);
            if (raw == null) return; // asset missing: silence, never a crash
            player = Manager.createPlayer(new ByteArrayInputStream(readAll(raw)), "audio/midi");
            // MMAPI's Player.LOOP_CONTINUOUSLY is the spec constant -1; the pinned microemu
            // API stub omits the constant (it has setLoopCount itself), so the literal is used.
            player.setLoopCount(-1);
            player.prefetch();
            player.start();
            current = path;
        } catch (Throwable t) {
            stop(); // release any half-made player; this device plays on silently
        } finally {
            try { if (raw != null) raw.close(); } catch (IOException ignored) { }
        }
    }

    /** Stops and releases the current track, if any. Safe to call when nothing plays. */
    void stop() {
        if (player == null) return;
        try { player.close(); } catch (Throwable ignored) { }
        player = null;
        current = null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
