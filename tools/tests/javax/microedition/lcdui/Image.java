package javax.microedition.lcdui;

import java.io.IOException;
import java.io.InputStream;

/**
 * Test-only stand-in for the MIDP {@code Image} class.
 *
 * <p>The pinned stub JARs in {@code deps/java-me/} make every {@code Image} member throw
 * {@code java.lang.Error("API Stub has been used")}. That is fine for unit tests, which never touch
 * images, but it blocks anything that constructs an {@code AssetLibrary} — and therefore anything
 * that constructs a real {@code RunWorld}. Since the whole point of the lifecycle integration test is
 * to drive the genuine constructor rather than a field-by-field reconstruction, this class replaces
 * the stub on the test classpath and hands back a cheap placeholder instead of throwing.
 *
 * <p>It is placed on the classpath ahead of the stub JARs and is compiled into a separate output
 * directory, so it affects tests only. Production source and the shipped JAR are untouched: the
 * build never compiles or packages this directory.
 *
 * <p>Only {@code createImage(String)} is reachable from production code that is not the canvas
 * render path. The remaining factories exist purely so the class links if something unexpected
 * calls them.
 */
public class Image {

    /** Counts how many images were requested, so tests can assert the loader actually ran. */
    public static int createCount;

    private final int width;
    private final int height;

    Image(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public static Image createImage(String path) throws IOException {
        createCount++;
        // A device returns an image or throws IOException. Headless we have no pixels, and the
        // size is irrelevant because no test asserts on rendering.
        return new Image(1, 1);
    }

    public static Image createImage(InputStream stream) throws IOException {
        createCount++;
        return new Image(1, 1);
    }

    /** Matches the stub's {@code createImage(byte[], int, int)}; the two spellings share a
     * descriptor, so only one can exist. */
    public static Image createImage(byte[] rgb, int width, int height) {
        createCount++;
        return new Image(width, height);
    }

    public static Image createImage(int width, int height) {
        createCount++;
        return new Image(width, height);
    }

    public static Image createImage(Image source) {
        createCount++;
        return new Image(1, 1);
    }

    public static Image createImage(Image source, int x, int y, int w, int h, int transform) {
        createCount++;
        return new Image(w, h);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        createCount++;
        return new Image(width, height);
    }

    public Graphics getGraphics() {
        throw new UnsupportedOperationException("no graphics off-device");
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isMutable() {
        return false;
    }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
        throw new UnsupportedOperationException("no pixels off-device");
    }
}
