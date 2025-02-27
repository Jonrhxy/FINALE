package com.example.firstpage;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class Game1 {
    public enum GameState { START, DIALOGUE, RUNNING, LEVEL_COMPLETE, LOST }

    private Context context;
    private SurfaceHolder holder;
    private Rect screen;
    private Resources resources;
    private GameState state = GameState.START;

    private Player1 player;
    // Background layers.
    private ScrollableBackground background_close, background_mid, background_far;
    // Obstacle (via Vehicle class).
    private Vehicle obstacle;
    private Sprite loseText;
    private Paint borderPaint = new Paint();
    private BitmapFactory.Options options;

    // Level management.
    private int currentLevel = 1;
    private int targetEcoPoints; // Points needed to complete the level.
    private String levelDescription; // e.g., "LEVEL 1: GREEN HOME".

    // For speed increases.
    private int speedIncrements = 0;
    // Count obstacles evaded.
    private int obstaclesEvadedCount = 0;
    // ECO Shield spawn cooldown timer (ms).
    private long ecoShieldSpawnCooldown = 0;

    // ECO Shield bitmap and effect.
    private Bitmap ecoshieldBmp;
    private Bitmap shieldEffectBmp;

    // Level complete image.
    private Bitmap levelCompBmp;
    private Rect levelCompRect;

    // Obstacle dimensions (fixed).
    private final int obstacleWidth = 230;
    private final int obstacleHeight = 230;

    // UI controls.
    private GameButtons gameButtons;
    private boolean paused = false;

    // Dialogue for pre-level instructions.
    private Dialogue dialogue;

    public Game1(Context context, Rect screen, SurfaceHolder holder, Resources resources) {
        this.context = context;
        this.screen = screen;
        this.holder = holder;
        this.resources = resources;
        options = new BitmapFactory.Options();
        options.inScaled = false;
        ecoshieldBmp = BitmapFactory.decodeResource(resources, R.drawable.ecoshield, options);
        shieldEffectBmp = BitmapFactory.decodeResource(resources, R.drawable.ecoshield_effect, options);
        levelCompBmp = BitmapFactory.decodeResource(resources, R.drawable.levelcomp, options);
        // Initialize UI buttons.
        gameButtons = new GameButtons(context, screen);
        setupLevel(1);
    }

    // Helper function to compute the rectangle for the level complete image.
    private Rect computeLevelCompRect() {
        // Semi-large: 75% of screen width and 50% of screen height.
        int endWidth = (int)(screen.width() * 0.75);
        int endHeight = (int)(screen.height() * 0.5);
        int endLeft = (screen.width() - endWidth) / 2;
        int endTop = (screen.height() - endHeight) / 2;
        return new Rect(endLeft, endTop, endLeft + endWidth, endTop + endHeight);
    }

    // Helper to compute the try button rectangle.
    private Rect computeTryButtonRect(Rect endRect) {
        int btnWidth = endRect.width() / 2;
        int btnHeight = endRect.height() / 2;
        int offsetBelowCenter = endRect.height() / 3;
        int btnTop = endRect.centerY() + offsetBelowCenter;
        int btnLeft = endRect.centerX() - (btnWidth / 2) - 50; // Shift left by 50 pixels.
        return new Rect(btnLeft, btnTop, btnLeft + btnWidth, btnTop + btnHeight);
    }

    // Helper to compute the next button rectangle independently.
    private Rect computeNextButtonRect(Rect endRect) {
        int btnWidth = endRect.width() / 2;
        int btnHeight = endRect.height() / 2;
        // Position next.png 10 pixels lower and 10 pixels to the right of the try button.
        Rect tryRect = computeTryButtonRect(endRect);
        int btnTop = tryRect.top + 10;
        int btnLeft = tryRect.left + 10;
        return new Rect(btnLeft, btnTop, btnLeft + btnWidth, btnTop + btnHeight);
    }

    public void onTouchEvent(MotionEvent event) {
        // In end states, only process touches on the buttons.
        if (state == GameState.LEVEL_COMPLETE || state == GameState.LOST) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                int btnResult = gameButtons.checkTouch(x, y, state);
                if (btnResult == 2) { // Next button touched.
                    if (currentLevel < 9) {
                        setupLevel(currentLevel + 1);
                    } else {
                        setupLevel(1);
                    }
                    state = GameState.RUNNING;
                } else if (btnResult == 3) { // Try button touched.
                    setupLevel(currentLevel);
                    state = GameState.RUNNING;
                }
            }
            return;
        }
        // In DIALOGUE state, a tap dismisses the dialogue (or starts the countdown).
        if (state == GameState.DIALOGUE) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dialogue.startCountdown();
            }
            return;
        }
        // In RUNNING or START states.
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            int btnResult = gameButtons.checkTouch(x, y, state);
            if (btnResult == 1) { // Pause/Play button touched.
                paused = gameButtons.isPaused();
                return;
            }
        }
        if (!paused) {
            if (state == GameState.RUNNING) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    player.jump();
                }
            } else if (state == GameState.START && event.getAction() == MotionEvent.ACTION_DOWN) {
                setupLevel(1);
                state = GameState.RUNNING;
            }
        }
    }

    public void update(Long elapsed) {
        if (state == GameState.DIALOGUE) {
            dialogue.update(elapsed);
            if (dialogue.getCountdown() <= 0) {
                state = GameState.RUNNING;
            }
            return;
        }
        if (state == GameState.RUNNING && !paused) {
            if (ecoShieldSpawnCooldown > 0) {
                ecoShieldSpawnCooldown -= elapsed;
                if (ecoShieldSpawnCooldown < 0) ecoShieldSpawnCooldown = 0;
            }
            player.update(elapsed);
            if (background_close != null) background_close.update(elapsed);
            if (background_mid != null) background_mid.update(elapsed);
            if (background_far != null) background_far.update(elapsed);
            obstacle.update(elapsed);

            if (obstacle.isOffScreen()) {
                obstaclesEvadedCount++;
                if (obstacle.getImage() != ecoshieldBmp) {
                    player.increaseScore();
                }
                if (obstaclesEvadedCount >= 10 && ecoShieldSpawnCooldown == 0) {
                    obstaclesEvadedCount = 0;
                    obstacle = createObstacleAtGround(ecoshieldBmp);
                    ecoShieldSpawnCooldown = 15000; // 15 sec cooldown.
                } else {
                    obstaclesEvadedCount = 0;
                    obstacle = createObstacleForLevel(currentLevel);
                }
            }
            if (currentLevel == 2) {
                int currentScore = player.getScore();
                if (currentScore / 5 > speedIncrements) {
                    speedIncrements = currentScore / 5;
                    if (background_close != null) background_close.speed += 2;
                    if (background_mid != null) background_mid.speed += 2;
                    if (background_far != null) background_far.speed += 2;
                    obstacle.vx -= 2;
                }
            }
            if (Rect.intersects(obstacle.getHitbox(), player.getHitbox())) {
                if (obstacle.getImage() == ecoshieldBmp) {
                    player.activateShield(5000); // 5 sec invincibility.
                    for (int i = 0; i < 5; i++) {
                        player.increaseScore();
                    }
                    obstacle = createObstacleForLevel(currentLevel);
                } else if (!player.isShieldActive()) {
                    loseGame();
                } else {
                    player.checkJumpOnVan(obstacle);
                }
            } else {
                player.checkJumpOnVan(obstacle);
            }
            if (player.getScore() >= targetEcoPoints) {
                state = GameState.LEVEL_COMPLETE;
            }
        }
    }

    public void draw() {
        Canvas canvas = holder.lockCanvas();
        if (canvas != null) {
            canvas.drawColor(Color.WHITE);
            drawGame(canvas);

            Paint levelPaint = new Paint();
            levelPaint.setColor(Color.WHITE);
            levelPaint.setTextSize(60);
            levelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(levelDescription, 50, 100, levelPaint);

            if (state == GameState.DIALOGUE) {
                dialogue.draw(canvas);
            }
            if (state == GameState.LEVEL_COMPLETE) {
                canvas.drawBitmap(levelCompBmp, null, levelCompRect, null);
            }
            if (state == GameState.LOST) {
                loseText.draw(canvas, 0);
            }
            gameButtons.draw(canvas, state);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawGame(Canvas canvas) {
        if (background_far != null) background_far.draw(canvas);
        if (background_mid != null) background_mid.draw(canvas);
        if (background_close != null) background_close.draw(canvas);
        obstacle.draw(canvas, 0);
        player.draw(canvas, 0);
    }

    private void setupLevel(int level) {
        currentLevel = level;
        speedIncrements = 0;
        obstaclesEvadedCount = 0;
        ecoShieldSpawnCooldown = 0;

        // Set target eco points and level description.
        switch (level) {
            case 1:
                levelDescription = "LEVEL 1: GREEN HOME";
                targetEcoPoints = 10;
                break;
            case 2:
                levelDescription = "LEVEL 2: ECO FACTORY";
                targetEcoPoints = 20;
                break;
            case 3:
                levelDescription = "LEVEL 3: SUSTAINABLE CITY";
                targetEcoPoints = 30;
                break;
            case 4:
                levelDescription = "LEVEL 4: GLOBAL ECO VILLAGE";
                targetEcoPoints = 40;
                break;
            case 5:
                levelDescription = "LEVEL 5: ECO WARRIOR";
                targetEcoPoints = 45;
                break;
            case 6:
                levelDescription = "LEVEL 6: SUSTAINABLE FUTURE";
                targetEcoPoints = 50;
                break;
            case 7:
                levelDescription = "LEVEL 7: ECO CHAMPION";
                targetEcoPoints = 55;
                break;
            case 8:
                levelDescription = "LEVEL 8: GLOBAL SUSTAINABILITY";
                targetEcoPoints = 60;
                break;
            case 9:
                levelDescription = "LEVEL 9: ECO MASTER";
                targetEcoPoints = 80;
                break;
            default:
                levelDescription = "LEVEL 1: GREEN HOME";
                targetEcoPoints = 10;
                break;
        }

        // Set up dialogue for pre-level instructions.
        // For the dialogue, pass both the bubble rectangle (for the dialogue text)
        // and the full-screen rectangle (to center the countdown timer).
        Rect dialogueBubble = new Rect(20, 20, screen.width() / 2, screen.height() / 2);
        dialogue = new Dialogue(
                context,
                dialogueBubble,
                screen,           // Full-screen rect.
                10000,            // 10-second countdown.
                "TAP TO JUMP BY AVOIDING OBSTACLES AND EARN ECO POINTS",
                "COLLECT " + targetEcoPoints + " ECOPOINTS TO COMPLETE THE LEVEL!!"
        );
        state = GameState.DIALOGUE;

        // Set up player.
        int groundY = screen.height() - screen.width() / 8;
        int playerHeight = 50;
        player = new Player1(context, new Rect(400, groundY - playerHeight - 20, 410, groundY - 20), screen);

        // Set up backgrounds and obstacle based on level.
        switch (level) {
            case 1:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl1_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 4);

                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl1_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 2);
                obstacle = createObstacleForLevel(1);
                break;
            case 2:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl2_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 10);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl2_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 6);
                obstacle = createObstacleForLevel(2);
                break;
            case 3:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl3_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 8);
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl3_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 4);
                obstacle = createObstacleForLevel(3);
                break;
            case 4:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl4_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 14);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl4_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 10);
                obstacle = createObstacleForLevel(4);
                break;
            case 5:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl5_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 16);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl5_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 12);
                obstacle = createObstacleForLevel(5);
                break;
            case 6:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl6_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 18);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl6_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 14);
                obstacle = createObstacleForLevel(6);
                break;
            case 7:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl7_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 20);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl7_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 16);
                obstacle = createObstacleForLevel(7);
                break;
            case 8:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl8_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 22);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl8_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 18);
                obstacle = createObstacleForLevel(8);
                break;
            case 9:
                background_close = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl9_close, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 24);
                background_mid = null;
                background_far = new ScrollableBackground(BitmapFactory.decodeResource(resources, R.drawable.lvl9_far, options),
                        context, new Rect(0, 0, screen.width(), screen.height()), screen, 20);
                obstacle = createObstacleForLevel(9);
                break;
            default:
                setupLevel(1);
                return;
        }

        // Compute endRect for end image.
        Rect endRect = computeLevelCompRect();
        loseText = new Sprite(BitmapFactory.decodeResource(resources, R.drawable.losetext, options),
                context, endRect, screen);
        levelCompRect = new Rect(endRect);

        // Compute try_btn rectangle.
        Rect tryRect = computeTryButtonRect(endRect);
        // Compute next.png rectangle independently.
        Rect nextRect = computeNextButtonRect(endRect);
        gameButtons.setTryButtonRect(tryRect);
        gameButtons.setNextButtonRect(nextRect);

        borderPaint.setStrokeWidth(24);
        borderPaint.setColor(Color.GREEN);
        borderPaint.setStyle(Paint.Style.STROKE);
        state = GameState.DIALOGUE;
    }

    private Vehicle createObstacleForLevel(int level) {
        if (obstaclesEvadedCount >= 10) {
            obstaclesEvadedCount = 0;
            return createObstacleAtGround(ecoshieldBmp);
        }

        double chance = Math.random();
        if (chance < 0.1) {
            return createObstacleAtGround(ecoshieldBmp);
        } else {
            int obstacleImageId;
            switch (level) {
                case 1:
                    int rand1 = (int) (Math.random() * 3);
                    if (rand1 == 0)
                        obstacleImageId = R.drawable.trashpilesl1;
                    else if (rand1 == 1)
                        obstacleImageId = R.drawable.wastefulappl11;
                    else
                        obstacleImageId = R.drawable.carbonmonster;
                    break;
                case 2:
                    int r2 = (int)(Math.random() * 2);
                    if (r2 == 0)
                        obstacleImageId = R.drawable.smokel2;
                    else
                        obstacleImageId = R.drawable.garbageheapsl2;
                    break;
                case 3:
                    int r3 = (int)(Math.random() * 2);
                    if (r3 == 0)
                        obstacleImageId = R.drawable.energywasterl3;
                    else
                        obstacleImageId = R.drawable.trashcanl3;
                    break;
                case 4:
                    int r4 = (int) (Math.random() * 3);
                    if (r4 == 0)
                        obstacleImageId = R.drawable.scalel4;
                    else if (r4 == 1)
                        obstacleImageId = R.drawable.documentl4;
                    else
                        obstacleImageId = R.drawable.piggybankl4;
                    break;
                case 5:
                    int r5 = (int)(Math.random() * 2);
                    if (r5 == 0)
                        obstacleImageId = R.drawable.cuttingtreesl5;
                    else
                        obstacleImageId = R.drawable.waterwastel5;
                    break;
                case 6:
                    int r6 = (int) (Math.random() * 4);
                    if (r6 == 0)
                        obstacleImageId = R.drawable.spillwastel6;
                    else if (r6 == 1)
                        obstacleImageId = R.drawable.smogl6;
                    else if (r6 == 2)
                        obstacleImageId = R.drawable.gasl6;
                    else
                        obstacleImageId = R.drawable.greenhousel6;
                    break;
                case 7:
                    int r7 = (int)(Math.random() * 2);
                    if (r7 == 0)
                        obstacleImageId = R.drawable.floodl7;
                    else
                        obstacleImageId = R.drawable.canl7;
                    break;
                case 8:
                    int r8 = (int)(Math.random() * 2);
                    if (r8 == 0)
                        obstacleImageId = R.drawable.scrolll8;
                    else
                        obstacleImageId = R.drawable.solarpanell8;
                    break;
                case 9:
                    int r9 = (int) (Math.random() * 3);
                    if (r9 == 0)
                        obstacleImageId = R.drawable.cutl9;
                    else if (r9 == 1)
                        obstacleImageId = R.drawable.whipl9;
                    else
                        obstacleImageId = R.drawable.firel9;
                    break;
                default:
                    obstacleImageId = R.drawable.waterwastel5;
                    break;
            }
            return createObstacleAtGround(BitmapFactory.decodeResource(resources, obstacleImageId, options));
        }
    }

    private Vehicle createObstacleAtGround(Bitmap obstacleBmp) {
        int groundY = screen.height() - screen.width() / 8;
        Rect obstacleRect = new Rect(screen.width(), groundY - obstacleHeight, screen.width() + obstacleWidth, groundY);
        return new Vehicle(obstacleBmp, context, obstacleRect, screen, groundY);
    }

    private void loseGame() {
        state = GameState.LOST;
    }
}
