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
            ensureStartingCapital();
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

    /**
     * Tops the balance up to {@link FullGameCanvas#SHOP_STARTING_CAPITAL} so the shop can be driven
     * before the coin economy has a real earning rate.
     *
     * <p>Deliberately a top-up rather than an assignment: coins earned in play are kept, so a player
     * who spends down and restarts is restored to the same ceiling instead of losing progress. The
     * result is ignored here exactly as it is everywhere else in SaveStore; the credit itself is
     * debounced like any other, and nothing here can write over a record that failed to load.
     *
     * <p>Delete this method and its one call site to remove the starting capital entirely.
     */
    private void ensureStartingCapital() {
        long capital = FullGameCanvas.SHOP_STARTING_CAPITAL;
        long current = save.coinBalance();
        if (current >= capital) return;
        save.creditCoins(capital - current);
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
