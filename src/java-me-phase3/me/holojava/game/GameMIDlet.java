package me.holojava.game;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

public class GameMIDlet extends MIDlet {
    private Display display;
    private HoloGameCanvas gameCanvas;

    protected void startApp() throws MIDletStateChangeException {
        if (gameCanvas == null) {
            Image[] playerIdle = loadFrames("/assets/phase3/spr_Gura_Idle/spr_Gura_Idle_", 3);
            Image[] playerRun = loadFrames("/assets/phase3/spr_Gura_Run/spr_Gura_Run_", 6);
            Image[] enemyFrames = loadFrames("/assets/phase3/spr_NousagiEnemyA/spr_NousagiEnemyA_", 3);
            Image projectile = loadSingle("/assets/phase3/spr_BulletTemp/spr_BulletTemp_0.png");
            Image[] xpFrames = loadFrames("/assets/phase3/spr_EXP/spr_EXP_", 12);
            gameCanvas = new HoloGameCanvas(playerIdle, playerRun, enemyFrames, projectile, xpFrames);
        }
        display = Display.getDisplay(this);
        display.setCurrent(gameCanvas);
        if (!gameCanvas.isRunning()) {
            new Thread(gameCanvas).start();
        }
        gameCanvas.resumeApplication();
    }

    protected void pauseApp() {
        if (gameCanvas != null) {
            gameCanvas.pauseApplication();
        }
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        if (gameCanvas != null) {
            gameCanvas.stopApplication();
        }
        display = null;
        gameCanvas = null;
    }

    private Image loadSingle(String path) {
        try {
            return Image.createImage(path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Image[] loadFrames(String prefix, int count) {
        Image[] result = new Image[count];
        for (int i = 0; i < count; i++) {
            try {
                result[i] = Image.createImage(prefix + i + ".png");
            } catch (Exception ignored) {
                result[i] = null;
            }
        }
        return result;
    }
}
