package me.holojava.slice;

import java.io.IOException;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

public class SliceMIDlet extends MIDlet {
    private Display display;
    private SliceGameCanvas gameCanvas;

    protected void startApp() throws MIDletStateChangeException {
        if (gameCanvas == null) {
            Image playerImage;
            Image enemyImage;
            try {
                playerImage = Image.createImage("/assets/player.png");
                enemyImage = Image.createImage("/assets/enemy.png");
            } catch (IOException exception) {
                throw new MIDletStateChangeException("Unable to load prototype asset");
            }
            gameCanvas = new SliceGameCanvas(playerImage, enemyImage);
        }
        display = Display.getDisplay(this);
        display.setCurrent(gameCanvas);
        if (!gameCanvas.isRunning()) {
            new Thread(gameCanvas).start();
        }
    }

    protected void pauseApp() {
        if (gameCanvas != null) {
            gameCanvas.pause();
        }
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        if (gameCanvas != null) {
            gameCanvas.stop();
        }
        display = null;
        gameCanvas = null;
    }
}
