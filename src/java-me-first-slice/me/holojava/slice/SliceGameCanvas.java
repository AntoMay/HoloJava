package me.holojava.slice;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;

public class SliceGameCanvas extends GameCanvas implements Runnable {
    private static final int PLAYER_SPEED = 3;
    private static final int ENEMY_SPEED = 1;
    private static final int MAX_HP = 5;
    private static final long INVULNERABILITY_MS = 1000;

    private Image playerImage;
    private Image enemyImage;

    private int playerX;
    private int playerY;
    private int enemyX;
    private int enemyY;

    private int hp = MAX_HP;
    private long invulnerableUntil;
    private long damageReady;
    private boolean gameOver;

    private volatile boolean stopped;
    private volatile boolean paused;
    private boolean running;

    public SliceGameCanvas(Image playerImage, Image enemyImage) {
        super(true);
        this.playerImage = playerImage;
        this.enemyImage = enemyImage;
        setFullScreenMode(true);
        resetRound();
    }

    public boolean isRunning() {
        return running;
    }

    public void pause() {
        paused = true;
    }

    public void stop() {
        stopped = true;
    }

    private void resetRound() {
        gameOver = false;
        hp = MAX_HP;
        invulnerableUntil = 0;
        damageReady = 0;
        int width = getWidth();
        int height = getHeight();
        int playerWidth = imageWidth(playerImage, 64);
        int playerHeight = imageHeight(playerImage, 64);
        int enemyWidth = imageWidth(enemyImage, 37);
        int enemyHeight = imageHeight(enemyImage, 55);
        playerX = (width - playerWidth) / 2;
        playerY = (height - playerHeight) / 2;
        enemyX = 8;
        enemyY = 8;
    }

    public void run() {
        running = true;
        long previous = System.currentTimeMillis();
        while (!stopped) {
            if (paused) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            long current = System.currentTimeMillis();
            int delta = (int) (current - previous);
            if (delta < 0) {
                delta = 0;
            } else if (delta > 50) {
                delta = 50;
            }
            previous = current;

            update();
            render();
            flushGraphics();

            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        running = false;
    }

    private void update() {
        int width = getWidth();
        int height = getHeight();
        int playerWidth = imageWidth(playerImage, 64);
        int playerHeight = imageHeight(playerImage, 64);
        int enemyWidth = imageWidth(enemyImage, 37);
        int enemyHeight = imageHeight(enemyImage, 55);

        int keys = getKeyStates();
        int deltaX = 0;
        int deltaY = 0;
        if ((keys & UP_PRESSED) != 0) {
            deltaY -= PLAYER_SPEED;
        }
        if ((keys & DOWN_PRESSED) != 0) {
            deltaY += PLAYER_SPEED;
        }
        if ((keys & LEFT_PRESSED) != 0) {
            deltaX -= PLAYER_SPEED;
        }
        if ((keys & RIGHT_PRESSED) != 0) {
            deltaX += PLAYER_SPEED;
        }
        if (deltaX < -PLAYER_SPEED) {
            deltaX = -PLAYER_SPEED;
        } else if (deltaX > PLAYER_SPEED) {
            deltaX = PLAYER_SPEED;
        }
        if (deltaY < -PLAYER_SPEED) {
            deltaY = -PLAYER_SPEED;
        } else if (deltaY > PLAYER_SPEED) {
            deltaY = PLAYER_SPEED;
        }

        playerX += deltaX;
        playerY += deltaY;

        if (playerX < 0) {
            playerX = 0;
        } else if (playerX + playerWidth > width) {
            playerX = width - playerWidth;
        }
        if (playerY < 0) {
            playerY = 0;
        } else if (playerY + playerHeight > height) {
            playerY = height - playerHeight;
        }

        long now = System.currentTimeMillis();

        if (!gameOver) {
            int xDistance = playerX - enemyX;
            int yDistance = playerY - enemyY;
            if (xDistance != 0 || yDistance != 0) {
                double distance = Math.sqrt((double) xDistance * xDistance + (double) yDistance * yDistance);
                if (distance > 0.0) {
                    int moveX = (int) ((xDistance * ENEMY_SPEED) / distance);
                    int moveY = (int) ((yDistance * ENEMY_SPEED) / distance);
                    enemyX += moveX;
                    enemyY += moveY;
                }
            }
            if (enemyX < 0) {
                enemyX = 0;
            } else if (enemyX + enemyWidth > width) {
                enemyX = width - enemyWidth;
            }
            if (enemyY < 0) {
                enemyY = 0;
            } else if (enemyY + enemyHeight > height) {
                enemyY = height - enemyHeight;
            }

            if (now >= damageReady && overlaps(playerX, playerY, playerWidth, playerHeight,
                    enemyX, enemyY, enemyWidth, enemyHeight)) {
                hp--;
                invulnerableUntil = now + INVULNERABILITY_MS;
                damageReady = invulnerableUntil;
                if (hp <= 0) {
                    hp = 0;
                    gameOver = true;
                }
            }
        } else if ((keys & FIRE_PRESSED) != 0) {
            resetRound();
        }
    }
    private void render() {
        int width = getWidth();
        int height = getHeight();
        Graphics g = getGraphics();
        if (g == null) {
            return;
        }

        g.setColor(255, 255, 255);
        g.fillRect(0, 0, width, height);

        long now = System.currentTimeMillis();
        if (enemyImage != null) {
            g.drawImage(enemyImage, enemyX, enemyY, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(192, 0, 0);
            g.fillRect(enemyX, enemyY, imageWidth(null, 37), imageHeight(null, 55));
        }

        boolean drawPlayer = true;
        if (now < invulnerableUntil) {
            long blinkPhase = (now / 120) % 2;
            drawPlayer = (blinkPhase == 0);
        }
        if (drawPlayer) {
            if (playerImage != null) {
                g.drawImage(playerImage, playerX, playerY, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(0, 128, 0);
                g.fillRect(playerX, playerY, 12, 12);
            }
        }

        g.setColor(0, 0, 0);
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(small);
        g.drawString("HP " + hp, 4, 4, Graphics.TOP | Graphics.LEFT);
        g.drawString("Touch enemy to take damage", 4, 4 + small.getHeight() + 2,
                Graphics.TOP | Graphics.LEFT);

        if (gameOver) {
            g.setColor(128, 128, 128);
            g.fillRect(0, (height / 2) - 24, width, 48);
            Font large = g.getFont();
            String message = "GAME OVER";
            g.setColor(0, 0, 0);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE));
            int largeHeight = g.getFont().getHeight();
            String retry = "Press FIRE to retry";
            g.drawString(message, width / 2, (height / 2) - largeHeight, Graphics.TOP | Graphics.HCENTER);
            g.setFont(small);
            g.drawString(retry, width / 2, (height / 2) + 2, Graphics.TOP | Graphics.HCENTER);
        }
    }

    private boolean overlaps(int firstX, int firstY, int firstWidth, int firstHeight,
            int secondX, int secondY, int secondWidth, int secondHeight) {
        return firstX < secondX + secondWidth
                && secondX < firstX + firstWidth
                && firstY < secondY + secondHeight
                && secondY < firstY + firstHeight;
    }

    private int imageWidth(Image image, int fallback) {
        return (image == null) ? fallback : image.getWidth();
    }

    private int imageHeight(Image image, int fallback) {
        return (image == null) ? fallback : image.getHeight();
    }
}
