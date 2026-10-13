package me.holojava.fullgame;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

public class FullGameMIDlet extends MIDlet implements FullGameCanvas.ShutdownRequest {
    private Display display;
    private FullGameCanvas canvas;
    private AssetLibrary assets;
    private SaveStore save;
    private Thread gameThread;

    protected void startApp() throws MIDletStateChangeException {
        if (canvas == null) {
            save = new SaveStore();
            String validation = ContentCatalog.validate();
            assets = new AssetLibrary();
            if (validation == null && assets.failures() > 0) {
                validation = "asset_load:" + assets.firstFailure();
            }
            canvas = new FullGameCanvas(assets, save, validation, this);
        }
        display = Display.getDisplay(this);
        display.setCurrent(canvas);
        if (gameThread == null || !canvas.isRunning()) {
            gameThread = new Thread(canvas, "holojava-fullgame");
            gameThread.start();
        }
        canvas.resumeApplication();
        if (canvas.exitRequested()) {
            notifyDestroyed();
        }
    }

    protected void pauseApp() {
        if (canvas != null) canvas.pauseApplication();
    }

    public void requestShutdown() {
        notifyDestroyed();
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        if (canvas != null) canvas.stopApplication();
        display = null;
        canvas = null;
        gameThread = null;
    }
}
