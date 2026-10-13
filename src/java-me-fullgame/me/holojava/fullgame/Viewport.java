package me.holojava.fullgame;

/**
 * Integer, allocation-free world/screen layout with 320x240 as the logical design resolution.
 * The simulation world expands on wide/tall screens instead of stretching sprites;
 * narrow screens use a centered crop of the minimum 320x240 world.
 * 
 * Scaling policy:
 * - Letterboxing is used when screen aspect ratio differs from 4:3 (320x240).
 * - The world expands horizontally on wide screens and vertically on tall screens.
 * - Offsets center the world within the viewport.
 * - Touch/key input mapping uses world coordinates; canvas dimensions are converted
 *   via screenX()/screenY() for consistent gameplay across display sizes.
 */
final class Viewport {
    static final int MIN_W = 320;
    static final int MIN_H = 240;

    int screenW;
    int screenH;
    int worldW = MIN_W;
    int worldH = MIN_H;
    int offsetX;
    int offsetY;

    void update(int width, int height) {
        screenW = width > 0 ? width : MIN_W;
        screenH = height > 0 ? height : MIN_H;
        worldW = screenW < MIN_W ? MIN_W : screenW;
        worldH = screenH < MIN_H ? MIN_H : screenH;
        offsetX = (screenW - worldW) / 2;
        offsetY = (screenH - worldH) / 2;
    }

    int screenX(int worldX) { return offsetX + worldX; }
    int screenY(int worldY) { return offsetY + worldY; }

    boolean contains(int worldX, int worldY) {
        return worldX >= 0 && worldY >= 0 && worldX < worldW && worldY < worldH;
    }

    String describe() {
        return screenW + "x" + screenH + " -> world " + worldW + "x" + worldH
                + " offset " + offsetX + "," + offsetY;
    }
}
