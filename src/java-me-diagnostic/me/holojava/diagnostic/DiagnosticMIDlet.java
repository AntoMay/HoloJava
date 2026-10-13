package me.holojava.diagnostic;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class DiagnosticMIDlet extends MIDlet {
    private Display display;
    private DiagnosticCanvas canvas;

    public DiagnosticMIDlet() {
        display = Display.getDisplay(this);
        canvas = new DiagnosticCanvas();
    }

    protected void startApp() throws MIDletStateChangeException {
        if (display == null) {
            display = Display.getDisplay(this);
        }
        if (canvas == null) {
            canvas = new DiagnosticCanvas();
        }
        display.setCurrent(canvas);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        canvas = null;
        display = null;
    }
}

class DiagnosticCanvas extends Canvas {
    private static final int LOGICAL_WIDTH = 320;
    private static final int LOGICAL_HEIGHT = 240;
    private String lastKey = "None";
    private int lastCode = 0;

    public DiagnosticCanvas() {
        setFullScreenMode(false);
    }

    public void keyPressed(int keyCode) {
        lastCode = keyCode;
        String name = getOptionalKeyName(keyCode);
        if (name == null || name.length() == 0) {
            lastKey = "key " + Integer.toString(lastCode);
        } else {
            lastKey = name + " (" + Integer.toString(lastCode) + ")";
        }
        repaint();
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();

        // Clear screen and draw a fixed 320x240 logical concept overlay.
        g.setColor(255, 255, 255);
        g.fillRect(0, 0, width, height);

        g.setColor(0, 0, 0);
        Font font = g.getFont();
        int lineHeight = font.getHeight() + 2;
        int x = 4;
        int y = 4;
        g.drawString("HoloJava Toolchain Test", x, y, Graphics.TOP | Graphics.LEFT);
        y += lineHeight;
        g.drawString("MIDlet launched successfully", x, y, Graphics.TOP | Graphics.LEFT);
        y += lineHeight;
        g.drawString("Logical target: " + LOGICAL_WIDTH + "x" + LOGICAL_HEIGHT,
                x, y, Graphics.TOP | Graphics.LEFT);
        y += lineHeight;
        g.drawString("Physical canvas: " + width + "x" + height,
                x, y, Graphics.TOP | Graphics.LEFT);
        y += lineHeight;
        g.drawString("Last key: " + lastKey,
                x, y, Graphics.TOP | Graphics.LEFT);
        y += lineHeight;
        g.drawString("Return from lazy-init check: OK", x, y,
                Graphics.TOP | Graphics.LEFT);

        // Lightweight logical-frame indicator. It intentionally does not rescale
        // or claim runtime scaling behavior.
        g.setColor(96, 96, 96);
        g.drawLine(0, height - 1, width - 1, 0);
        g.drawRect(0, 0, width - 1, height - 1);
    }

    private String getOptionalKeyName(int keyCode) {
        try {
            return getKeyName(keyCode);
        } catch (Exception exception) {
            return null;
        }
    }
}
