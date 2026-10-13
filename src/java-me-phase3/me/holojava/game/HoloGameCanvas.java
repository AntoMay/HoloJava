package me.holojava.game;

import java.util.Random;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;

public class HoloGameCanvas extends GameCanvas implements Runnable {
    private static final int LOG_W = 320;
    private static final int LOG_H = 240;

    private static final long TICK_MS = 40;
    private static final int MAX_UPDATES = 3;
    private static final int ENEMY_POOL = 16;
    private static final int ENEMY_CAP = 12;
    private static final int SHOT_POOL = 24;
    private static final int XP_POOL = 64;

    private static final int STATE_MENU = 1;
    private static final int STATE_RUNNING = 2;
    private static final int STATE_LEVEL_UP = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_GAME_OVER = 5;

    private final Random random = new Random();
    private final Image[] idleFrames;
    private final Image[] runFrames;
    private final Image[] enemyFrames;
    private final Image shotImage;
    private final Image[] xpFrames;

    private volatile boolean stopped;
    private volatile boolean threadRunning;
    private volatile boolean appPaused;
    private boolean fireWasDown;
    private boolean upWasDown;
    private boolean downWasDown;

    private int state;
    private int pausedState;
    private long gameStart;

    private int px;
    private int py;
    private int playerSpeed = 2;
    private int playerHp = 10;
    private int playerHpMax = 10;
    private final int playerCollW = 24;
    private final int playerCollH = 22;
    private long invulUntil;
    private int weaponDamage = 1;
    private int weaponDelay = 800;
    private long weaponReady;
    private boolean moving;
    private int facingX = 1;
    private int level = 1;
    private int experience = 0;
    private int levelNeed = 12;
    private int kills = 0;

    private long nextSpawn;
    private long nextDifficulty;

    private class Enemy {
        boolean active;
        int x;
        int y;
        int hp;
        int hpMax;
        int damage;
        int speed;
        long nextAttack;
        int animFrame;
        long nextAnim;
    }

    private final Enemy[] enemies = new Enemy[ENEMY_POOL];

    private class Shot {
        boolean active;
        int x;
        int y;
        int dx;
        int dy;
        int lifetime;
        int damage;
    }

    private final Shot[] shots = new Shot[SHOT_POOL];

    private class Pickup {
        boolean active;
        int x;
        int y;
        int value;
        long born;
    }

    private final Pickup[] pickups = new Pickup[XP_POOL];

    private int upgradeIndex;

    public HoloGameCanvas(Image[] idleFrames, Image[] runFrames, Image[] enemyFrames,
            Image shotImage, Image[] xpFrames) {
        super(true);
        this.idleFrames = idleFrames;
        this.runFrames = runFrames;
        this.enemyFrames = enemyFrames;
        this.shotImage = shotImage;
        this.xpFrames = xpFrames;
        setFullScreenMode(true);

        for (int i = 0; i < ENEMY_POOL; i++) {
            enemies[i] = new Enemy();
        }
        for (int i = 0; i < SHOT_POOL; i++) {
            shots[i] = new Shot();
        }
        for (int i = 0; i < XP_POOL; i++) {
            pickups[i] = new Pickup();
        }
        state = STATE_MENU;
    }

    public boolean isRunning() {
        return threadRunning;
    }

    public void pauseApplication() {
        if (!appPaused) {
            appPaused = true;
            pausedState = state;
            if (state == STATE_RUNNING) {
                state = STATE_PAUSED;
            }
        }
    }

    public void resumeApplication() {
        if (appPaused) {
            appPaused = false;
            if (state == STATE_PAUSED) {
                state = pausedState;
            }
        }
    }

    public void stopApplication() {
        stopped = true;
    }

    public void run() {
        threadRunning = true;
        long last = System.currentTimeMillis();
        long accumulator = 0;
        while (!stopped) {
            if (appPaused) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                last = System.currentTimeMillis();
                continue;
            }

            long now = System.currentTimeMillis();
            long delta = now - last;
            if (delta > 200) {
                delta = 200;
            }
            if (delta < 0) {
                delta = 0;
            }
            last = now;
            accumulator += delta;

            int updates = 0;
            while (accumulator >= TICK_MS && updates < MAX_UPDATES) {
                updateOne();
                accumulator -= TICK_MS;
                updates++;
            }

            render();
            flushGraphics();

            try {
                Thread.sleep(16);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        threadRunning = false;
    }

    private void updateOne() {
        long now = System.currentTimeMillis();
        if (state == STATE_PAUSED || appPaused) {
            return;
        }
        if (state == STATE_MENU) {
            updateMenu(now);
            return;
        }
        if (state == STATE_GAME_OVER) {
            updateGameOver();
            return;
        }
        if (state == STATE_LEVEL_UP) {
            updateLevelUp();
            return;
        }
        if (state == STATE_RUNNING) {
            updateRun(now);
        }
    }

    private void updateMenu(long now) {
        int keys = getKeyStates();
        if ((keys & FIRE_PRESSED) != 0) {
            if (!fireWasDown) {
                startRun(now);
            }
            fireWasDown = true;
        } else {
            fireWasDown = false;
        }
    }

    private void updateGameOver() {
        int keys = getKeyStates();
        if ((keys & FIRE_PRESSED) != 0) {
            if (!fireWasDown) {
                state = STATE_MENU;
            }
            fireWasDown = true;
        } else {
            fireWasDown = false;
        }
    }

    private void updateLevelUp() {
        int keys = getKeyStates();
        boolean firePressed = (keys & FIRE_PRESSED) != 0;
        boolean upPressed = (keys & UP_PRESSED) != 0;
        boolean downPressed = (keys & DOWN_PRESSED) != 0;

        if (upPressed && !upWasDown) {
            upgradeIndex = (upgradeIndex + 3) % 4;
        }
        if (downPressed && !downWasDown) {
            upgradeIndex = (upgradeIndex + 1) % 4;
        }
        upWasDown = upPressed;
        downWasDown = downPressed;

        if (firePressed && !fireWasDown) {
            applyUpgrade(upgradeIndex, System.currentTimeMillis());
            state = STATE_RUNNING;
            fireWasDown = true;
        } else if (!firePressed) {
            fireWasDown = false;
        }
    }

    private void startRun(long now) {
        for (int i = 0; i < ENEMY_POOL; i++) {
            enemies[i].active = false;
        }
        for (int i = 0; i < SHOT_POOL; i++) {
            shots[i].active = false;
        }
        for (int i = 0; i < XP_POOL; i++) {
            pickups[i].active = false;
        }
        px = LOG_W / 2 - playerCollW / 2;
        py = LOG_H / 2 - playerCollH / 2;
        playerHp = playerHpMax = 10;
        playerSpeed = 2;
        weaponDamage = 1;
        weaponDelay = 800;
        weaponReady = now + weaponDelay;
        invulUntil = 0;
        level = 1;
        experience = 0;
        levelNeed = 12;
        kills = 0;
        nextSpawn = now + 1200;
        nextDifficulty = now + 10000;
        gameStart = now;
        state = STATE_RUNNING;
        fireWasDown = true;
    }

    private void updateRun(long now) {
        long elapsed = now - gameStart;
        int keys = getKeyStates();
        int dx = 0;
        int dy = 0;
        if ((keys & LEFT_PRESSED) != 0) {
            dx -= playerSpeed;
        }
        if ((keys & RIGHT_PRESSED) != 0) {
            dx += playerSpeed;
        }
        if ((keys & UP_PRESSED) != 0) {
            dy -= playerSpeed;
        }
        if ((keys & DOWN_PRESSED) != 0) {
            dy += playerSpeed;
        }
        moving = (dx != 0 || dy != 0);
        if (dx < -playerSpeed) {
            dx = -playerSpeed;
        } else if (dx > playerSpeed) {
            dx = playerSpeed;
        }
        if (dy < -playerSpeed) {
            dy = -playerSpeed;
        } else if (dy > playerSpeed) {
            dy = playerSpeed;
        }
        px += dx;
        py += dy;
        if (dx > 0) {
            facingX = 1;
        } else if (dx < 0) {
            facingX = -1;
        }
        if (px < 0) {
            px = 0;
        } else if (px + playerCollW > LOG_W) {
            px = LOG_W - playerCollW;
        }
        if (py < 0) {
            py = 0;
        } else if (py + playerCollH > LOG_H) {
            py = LOG_H - playerCollH;
        }

        int enemyHp = 2 + (int) (elapsed / 20000L) + (level / 5);
        if (enemyHp > 24) {
            enemyHp = 24;
        }
        int enemyDamage = 1 + (int) (elapsed / 25000L);
        if (enemyDamage > 4) {
            enemyDamage = 4;
        }
        int enemySpeed = 1 + (int) (elapsed / 60000L);
        if (enemySpeed > 2) {
            enemySpeed = 2;
        }

        /* spawn */
        if (now >= nextSpawn && activeEnemyCount() < ENEMY_CAP) {
            spawnEnemy(now, enemyHp, enemyDamage, enemySpeed);
            int spawnInterval = 1500 - (int) (elapsed / 50);
            if (spawnInterval < 400) {
                spawnInterval = 400;
            }
            nextSpawn = now + spawnInterval;
        }

        /* enemies */
        long centerPlayerX = px + playerCollW / 2;
        long centerPlayerY = py + playerCollH / 2;
        for (int i = 0; i < ENEMY_POOL; i++) {
            Enemy enemy = enemies[i];
            if (!enemy.active) {
                continue;
            }
            int enemyCenterX = enemy.x + enemyFrameWidth() / 2;
            int enemyCenterY = enemy.y + enemyFrameHeight() / 2;
            int stepX = centerPlayerX > enemyCenterX ? 1 : centerPlayerX < enemyCenterX ? -1 : 0;
            int stepY = centerPlayerY > enemyCenterY ? 1 : centerPlayerY < enemyCenterY ? -1 : 0;
            if (stepX < 0 && enemy.speed >= 1) {
                enemy.x -= enemy.speed;
            } else if (stepX > 0) {
                enemy.x += enemy.speed;
            }
            if (stepY < 0 && enemy.speed >= 1) {
                enemy.y -= enemy.speed;
            } else if (stepY > 0) {
                enemy.y += enemy.speed;
            }
            if (enemy.x < 0) {
                enemy.x = 0;
            } else if (enemy.x + enemyFrameWidth() > LOG_W) {
                enemy.x = LOG_W - enemyFrameWidth();
            }
            if (enemy.y < 0) {
                enemy.y = 0;
            } else if (enemy.y + enemyFrameHeight() > LOG_H) {
                enemy.y = LOG_H - enemyFrameHeight();
            }

            if (now >= enemy.nextAnim) {
                enemy.nextAnim = now + 150;
                enemy.animFrame = (enemy.animFrame + 1) % 3;
            }

            /* contact damage */
            if (now >= invulUntil && now >= enemy.nextAttack
                    && enemyFramesOverlap(px, py, playerCollW, playerCollH,
                            enemy.x, enemy.y, enemyFrameWidth(), enemyFrameHeight())) {
                playerHp -= enemy.damage;
                enemy.nextAttack = now + 650;
                invulUntil = now + 1000;
                if (playerHp <= 0) {
                    playerHp = 0;
                    state = STATE_GAME_OVER;
                    return;
                }
            }
        }

        /* weapon */
        if (now >= weaponReady && activeEnemyCount() > 0) {
            fireShot(now);
            weaponReady = now + weaponDelay;
        }

        /* projectiles */
        for (int i = 0; i < SHOT_POOL; i++) {
            Shot shot = shots[i];
            if (!shot.active) {
                continue;
            }
            shot.x += shot.dx;
            shot.y += shot.dy;
            shot.lifetime--;
            if (shot.lifetime <= 0 || shot.x < -20 || shot.y < -20
                    || shot.x > LOG_W + 20 || shot.y > LOG_H + 20) {
                shot.active = false;
                continue;
            }
            for (int e = 0; e < ENEMY_POOL; e++) {
                Enemy enemy = enemies[e];
                if (!enemy.active) {
                    continue;
                }
                if (framesOverlap(shot.x, shot.y, shotWidth(), shotHeight(),
                        enemy.x, enemy.y, enemyFrameWidth(), enemyFrameHeight())) {
                    enemy.hp -= shot.damage;
                    shot.active = false;
                    if (enemy.hp <= 0) {
                        enemy.active = false;
                        kills++;
                        dropPickup(enemy.x + enemyFrameWidth() / 2, enemy.y + enemyFrameHeight() / 2, level);
                    }
                    break;
                }
            }
        }

        /* pickups */
        for (int i = 0; i < XP_POOL; i++) {
            Pickup pickup = pickups[i];
            if (!pickup.active) {
                continue;
            }
            int pupCenterX = pickup.x + 6;
            int pupCenterY = pickup.y + 6;
            int playerCenterX = px + playerCollW / 2;
            int playerCenterY = py + playerCollH / 2;
            int dxp = playerCenterX - pupCenterX;
            int dyp = playerCenterY - pupCenterY;
            int distSq = dxp * dxp + dyp * dyp;
            int pickupRadius = 18;
            if (distSq <= pickupRadius * pickupRadius) {
                experience += pickup.value;
                pickup.active = false;
                if (experience >= levelNeed) {
                    experience -= levelNeed;
                    level++;
                    levelNeed = 12 + (level * 8);
                    if (levelNeed > 120) {
                        levelNeed = 120;
                    }
                    state = STATE_LEVEL_UP;
                    upgradeIndex = 0;
                    return;
                }
            } else if (distSq <= 96 * 96) {
                if (dxp > 0) {
                    pickup.x += 3;
                } else if (dxp < 0) {
                    pickup.x -= 3;
                }
                if (dyp > 0) {
                    pickup.y += 3;
                } else if (dyp < 0) {
                    pickup.y -= 3;
                }
            }
            if (now - pickup.born > 12000) {
                pickup.active = false;
            }
        }
    }

    private int activeEnemyCount() {
        int total = 0;
        for (int i = 0; i < ENEMY_POOL; i++) {
            if (enemies[i].active) {
                total++;
            }
        }
        return total;
    }

    private int enemyFrameWidth() {
        return enemyFrames != null && enemyFrames.length > 0 && enemyFrames[0] != null
                ? enemyFrames[0].getWidth() : 24;
    }

    private int enemyFrameHeight() {
        return enemyFrames != null && enemyFrames.length > 0 && enemyFrames[0] != null
                ? enemyFrames[0].getHeight() : 22;
    }

    private int shotWidth() {
        return shotImage != null ? shotImage.getWidth() : 7;
    }

    private int shotHeight() {
        return shotImage != null ? shotImage.getHeight() : 7;
    }

    private void spawnEnemy(long now, int enemyHp, int enemyDamage, int enemySpeed) {
        int index = -1;
        for (int i = 0; i < ENEMY_POOL; i++) {
            if (!enemies[i].active) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return;
        }
        Enemy enemy = enemies[index];
        enemy.active = true;
        enemy.hp = enemyHp;
        enemy.hpMax = enemyHp;
        enemy.damage = enemyDamage;
        enemy.speed = enemySpeed;
        for (int tries = 0; tries < 10; tries++) {
            int side = random.nextInt() & 3;
            if (side == 0) {
                enemy.x = randomRange(0, Math.max(1, LOG_W - enemyFrameWidth()));
                enemy.y = -enemyFrameHeight();
            } else if (side == 1) {
                enemy.x = LOG_W;
                enemy.y = randomRange(0, Math.max(1, LOG_H - enemyFrameHeight()));
            } else if (side == 2) {
                enemy.x = randomRange(0, Math.max(1, LOG_W - enemyFrameWidth()));
                enemy.y = LOG_H;
            } else {
                enemy.x = -enemyFrameWidth();
                enemy.y = randomRange(0, Math.max(1, LOG_H - enemyFrameHeight()));
            }
            long dxPlayer = Math.abs(enemy.x - (px + playerCollW / 2));
            long dyPlayer = Math.abs(enemy.y - (py + playerCollH / 2));
            long distSq = dxPlayer * dxPlayer + dyPlayer * dyPlayer;
            if (distSq >= 80 * 80 || tries == 9) {
                break;
            }
        }
        enemy.nextAttack = now;
        enemy.animFrame = 0;
        enemy.nextAnim = now + 150;
    }

    private int randomRange(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + Math.abs(random.nextInt()) % (max - min + 1);
    }

    private void fireShot(long now) {
        int index = -1;
        for (int i = 0; i < SHOT_POOL; i++) {
            if (!shots[i].active) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return;
        }
        /* nearest enemy */
        int nearest = -1;
        long nearestSq = Long.MAX_VALUE;
        long centerX = px + playerCollW / 2;
        long centerY = py + playerCollH / 2;
        for (int i = 0; i < ENEMY_POOL; i++) {
            Enemy enemy = enemies[i];
            if (!enemy.active) {
                continue;
            }
            long dx = (enemy.x + enemyFrameWidth() / 2) - centerX;
            long dy = (enemy.y + enemyFrameHeight() / 2) - centerY;
            long distSq = dx * dx + dy * dy;
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = i;
            }
        }
        if (nearest == -1) {
            return;
        }
        Enemy target = enemies[nearest];
        Shot shot = shots[index];
        int dxTarget = (target.x + enemyFrameWidth() / 2) - (int) centerX;
        int dyTarget = (target.y + enemyFrameHeight() / 2) - (int) centerY;
        double dist = Math.sqrt((double) dxTarget * dxTarget + (double) dyTarget * dyTarget);
        int speed = 8;
        if (dist > 0.0) {
            shot.dx = (int) ((dxTarget * speed) / dist);
            shot.dy = (int) ((dyTarget * speed) / dist);
        } else {
            shot.dx = facingX * speed;
            shot.dy = 0;
        }
        if (shot.dx == 0 && shot.dy == 0) {
            shot.dx = facingX * speed;
            shot.dy = facingX > 0 ? -1 : 1;
        }
        int shotCenterX = (int) centerX - shotWidth() / 2;
        int shotCenterY = (int) centerY - shotHeight() / 2;
        shot.x = shotCenterX;
        shot.y = shotCenterY;
        shot.lifetime = 70;
        shot.damage = weaponDamage;
        shot.active = true;
        return;
    }

    private void dropPickup(int x, int y, int dropLevel) {
        for (int i = 0; i < XP_POOL; i++) {
            if (!pickups[i].active) {
                Pickup pickup = pickups[i];
                pickup.active = true;
                pickup.x = x - 6;
                pickup.y = y - 6;
                pickup.value = 1 + (dropLevel / 5);
                if (pickup.value > 3) {
                    pickup.value = 3;
                }
                pickup.born = System.currentTimeMillis();
                return;
            }
        }
    }

    private void applyUpgrade(int choice, long now) {
        switch (choice) {
            case 0:
                if (weaponDamage < 5) {
                    weaponDamage++;
                }
                break;
            case 1:
                if (weaponDelay > 120) {
                    weaponDelay -= 80;
                    if (weaponDelay < 120) {
                        weaponDelay = 120;
                    }
                }
                break;
            case 2:
                if (playerSpeed < 4) {
                    playerSpeed++;
                }
                break;
            case 3:
                playerHpMax += 2;
                playerHp += 2;
                if (playerHpMax > 40) {
                    playerHpMax = 40;
                }
                if (playerHp > playerHpMax) {
                    playerHp = playerHpMax;
                }
                break;
            default:
                break;
        }
        nextSpawn = now + 400;
        weaponReady = now + weaponDelay;
    }

    private boolean enemyFramesOverlap(int ax, int ay, int aw, int ah,
            int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    private boolean framesOverlap(int ax, int ay, int aw, int ah,
            int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    private void render() {
        int canvasW = getWidth();
        int canvasH = getHeight();
        int offX = (canvasW - LOG_W) / 2;
        int offY = (canvasH - LOG_H) / 2;
        if (offX < 0) {
            offX = 0;
        }
        if (offY < 0) {
            offY = 0;
        }

        Graphics g = getGraphics();
        if (g == null) {
            return;
        }

        g.setColor(0, 0, 0);
        g.fillRect(0, 0, canvasW, canvasH);

        g.setClip(offX, offY, LOG_W, LOG_H);
        g.setColor(18, 30, 24);
        g.fillRect(offX, offY, LOG_W, LOG_H);
        g.setColor(22, 40, 30);
        for (int x = offX; x < offX + LOG_W; x += 40) {
            g.fillRect(x, offY, 1, LOG_H);
        }
        for (int y = offY; y < offY + LOG_H; y += 40) {
            g.fillRect(offX, y, LOG_W, 1);
        }

        long now = System.currentTimeMillis();

        /* XP pickups first */
        for (int i = 0; i < XP_POOL; i++) {
            Pickup pickup = pickups[i];
            if (!pickup.active) {
                continue;
            }
            int frame = (int) (((now - pickup.born) / 70) % 12);
            int fx = frame;
            if (fx >= xpFrames.length) {
                fx = 0;
            }
            int drawX = offX + pickup.x;
            int drawY = offY + pickup.y;
            if (xpFrames != null && fx < xpFrames.length && xpFrames[fx] != null) {
                g.drawImage(xpFrames[fx], drawX, drawY, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(230, 230, 0);
                g.fillRect(drawX, drawY, 12, 13);
            }
        }

        /* projectiles */
        for (int i = 0; i < SHOT_POOL; i++) {
            Shot shot = shots[i];
            if (!shot.active) {
                continue;
            }
            int drawX = offX + shot.x;
            int drawY = offY + shot.y;
            if (shotImage != null) {
                g.drawImage(shotImage, drawX, drawY, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(255, 255, 255);
                g.fillRect(drawX, drawY, 7, 7);
            }
        }

        /* enemies */
        for (int i = 0; i < ENEMY_POOL; i++) {
            Enemy enemy = enemies[i];
            if (!enemy.active) {
                continue;
            }
            int drawX = offX + enemy.x;
            int drawY = offY + enemy.y;
            Image frame = enemyFrames != null && enemyFrames.length > 0
                    ? enemyFrames[enemy.animFrame % enemyFrames.length] : null;
            if (frame != null) {
                g.drawImage(frame, drawX, drawY, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(220, 0, 0);
                g.fillRect(drawX, drawY, 24, 22);
            }
        }

        /* player */
        long elapsedRun = (state == STATE_RUNNING || state == STATE_LEVEL_UP) ? now - gameStart : 0;
        boolean showPlayer = true;
        if (state == STATE_RUNNING && now < invulUntil) {
            showPlayer = ((now / 110) % 2 == 0);
        }
        Image[] playerFrames = moving ? runFrames : idleFrames;
        int playerFrameCount = 0;
        if (playerFrames != null) {
            playerFrameCount = playerFrames.length;
        }
        int playerFrameIndex = 0;
        if (playerFrameCount > 0) {
            long period = moving ? 90 : 160;
            playerFrameIndex = (int) ((now / period) % playerFrameCount);
            if (playerFrameIndex < 0 || playerFrameIndex >= playerFrameCount) {
                playerFrameIndex = 0;
            }
        }
        if (state == STATE_RUNNING || state == STATE_LEVEL_UP || state == STATE_MENU) {
            if (showPlayer) {
                int drawX = offX + px;
                int drawY = offY + py;
                if (playerFrames != null && playerFrameIndex < playerFrameCount
                        && playerFrames[playerFrameIndex] != null) {
                    g.drawImage(playerFrames[playerFrameIndex], drawX, drawY,
                            Graphics.TOP | Graphics.LEFT);
                } else {
                    g.setColor(0, 140, 0);
                    g.fillRect(drawX, drawY, playerCollW, playerCollH);
                }
            }
        }

        g.setClip(0, 0, canvasW, canvasH);

        /* HUD */
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font medium = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        g.setFont(small);
        g.setColor(0, 0, 0);

        if (state == STATE_MENU) {
            g.setFont(medium);
            g.drawString("HoloCure Clone Slice", 16, 40, Graphics.TOP | Graphics.LEFT);
            g.setFont(small);
            g.drawString("Press FIRE to start", 16, 75, Graphics.TOP | Graphics.LEFT);
            g.drawString("D-pad moves, FIRE fires", 16, 95, Graphics.TOP | Graphics.LEFT);
        } else if (state == STATE_RUNNING || state == STATE_LEVEL_UP || state == STATE_PAUSED) {
            g.drawString("HP " + playerHp + "/" + playerHpMax, 8, 8, Graphics.TOP | Graphics.LEFT);
            g.drawString("L " + level, 8, 24, Graphics.TOP | Graphics.LEFT);
            g.drawString("XP " + experience + "/" + levelNeed, 8, 40, Graphics.TOP | Graphics.LEFT);
            g.drawString("Time " + (elapsedRun / 1000), 8, 56, Graphics.TOP | Graphics.LEFT);
            g.drawString("Kills " + kills, 8, 72, Graphics.TOP | Graphics.LEFT);
        }

        if (state == STATE_LEVEL_UP) {
            int boxX = 48;
            int boxY = 48;
            int boxW = LOG_W - 96;
            int boxH = LOG_H - 96;
            g.setColor(0, 0, 0);
            g.fillRect(offX + boxX, offY + boxY, boxW, boxH);
            g.setColor(255, 255, 255);
            g.drawRect(offX + boxX, offY + boxY, boxW, boxH);
            int lineY = offY + boxY + 6;
            g.drawString("Level up! Choose upgrade:", offX + boxX + 8, lineY,
                    Graphics.TOP | Graphics.LEFT);
            String[] choices = {"1. +Damage", "2. +Rate", "3. +Speed", "4. +HP"};
            for (int i = 0; i < 4; i++) {
                g.setColor(i == upgradeIndex ? 255 : 180, i == upgradeIndex ? 240 : 180,
                        i == upgradeIndex ? 100 : 180);
                g.drawString(choices[i], offX + boxX + 12, lineY + 18 + (i * 16),
                        Graphics.TOP | Graphics.LEFT);
            }
            g.setColor(200, 200, 200);
            g.drawString("D-pad select, FIRE confirm", offX + boxX + 8,
                    offY + boxY + boxH - 18, Graphics.TOP | Graphics.LEFT);
        }

        if (state == STATE_PAUSED) {
            g.setColor(128, 128, 128);
            g.fillRect(offX + 64, offY + 72, 192, 96);
            g.setColor(0, 0, 0);
            g.drawString("PAUSED", offX + LOG_W / 2, offY + LOG_H / 2 - 10,
                    Graphics.TOP | Graphics.HCENTER);
        }

        if (state == STATE_GAME_OVER) {
            g.setColor(96, 0, 0);
            g.fillRect(offX + 32, offY + 60, LOG_W - 64, 120);
            g.setColor(255, 255, 255);
            g.setFont(medium);
            g.drawString("GAME OVER", offX + LOG_W / 2, offY + 80,
                    Graphics.TOP | Graphics.HCENTER);
            g.setFont(small);
            g.drawString("Reached level " + level, offX + LOG_W / 2, offY + 120,
                    Graphics.TOP | Graphics.HCENTER);
            g.drawString("Time " + (elapsedRun / 1000), offX + LOG_W / 2, offY + 140,
                    Graphics.TOP | Graphics.HCENTER);
            g.drawString("Press FIRE to menu", offX + LOG_W / 2, offY + 160,
                    Graphics.TOP | Graphics.HCENTER);
        }

        g.setClip(0, 0, (canvasW > 0 ? canvasW : LOG_W), (canvasH > 0 ? canvasH : LOG_H));
    }
}
