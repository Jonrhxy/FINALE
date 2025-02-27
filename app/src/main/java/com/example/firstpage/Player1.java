package com.example.firstpage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

public class Player1 extends Sprite {
    private Bitmap[] runningFrames;
    private int frameIndex = 0;
    private long lastFrameChangeTime = 0;
    private int frameDelay = 100; // Delay between frames in milliseconds
    private int score = 0;
    private Paint scorePaint;
    private boolean onVan = false;
    private long scoreAccumulator = 0; // (Removed auto-increment logic here)

    // ECO Shield fields
    private boolean shieldActive = false;
    private long shieldTimer = 0; // Duration in milliseconds (5 seconds)
    private Bitmap shieldEffectBmp; // ECO Shield effect image

    public Player1(Context context, Rect hitbox, Rect screen) {
        super(null, context, hitbox, screen);
        this.affectedByGrav = true;

        runningFrames = new Bitmap[]{
                BitmapFactory.decodeResource(context.getResources(), R.drawable.run),
                BitmapFactory.decodeResource(context.getResources(), R.drawable.run1),
                BitmapFactory.decodeResource(context.getResources(), R.drawable.run2)
        };

        this.setImage(runningFrames[0]);

        scorePaint = new Paint();
        scorePaint.setColor(Color.BLACK);
        scorePaint.setTextSize(90);
        scorePaint.setTextAlign(Paint.Align.CENTER);

        // Load ECO Shield effect image.
        shieldEffectBmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ecoshield_effect, null);
    }

    @Override
    public void update(long elapsed) {
        // Update shield timer if shield is active.
        if (shieldActive) {
            shieldTimer -= elapsed;
            if (shieldTimer <= 0) {
                shieldActive = false;
            }
        }

        // Ensure the player doesn't fall below the ground.
        if (this.getHitbox().bottom >= screen.height() - screen.width() / 10) {
            this.setY(screen.height() - screen.width() / 10 - this.getHeight());
            this.vy = 0;
        }

        animate();
        super.update(elapsed);
        this.ax = this.ay = 0;
    }

    private void animate() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameChangeTime > frameDelay) {
            frameIndex = (frameIndex + 1) % runningFrames.length;
            this.setImage(runningFrames[frameIndex]);
            lastFrameChangeTime = currentTime;
        }
    }

    public void jump() {
        if (Math.abs(this.getBottom() - screen.height() + screen.width() / 10) < 5 || onVan) {
            this.applyForce(0, -60);
            onVan = false;
        }
    }

    public void applyForce(double fax, double fay) {
        this.ax = fax;
        this.ay = fay;
    }

    @Override
    public void draw(Canvas canvas, long elevation) {
        // Draw the player's current frame.
        if (this.getImage() != null) {
            canvas.drawBitmap(this.getImage(), (float)this.getX(), (float)this.getY(), null);
        }
        // Draw the scoreboard at the top center.
        canvas.drawText("Score: " + score, screen.width() / 2, 100, scorePaint);

        // If ECO Shield is active, draw the ECO Shield effect merged with the player's sprite.
        if (shieldActive) {
            Rect hitbox = getHitbox();
            // Scale the shield effect image to exactly match the player's dimensions.
            Bitmap scaledShieldEffect = Bitmap.createScaledBitmap(shieldEffectBmp, hitbox.width(), hitbox.height(), false);
            // Draw the shield effect at the same coordinates as the player's hitbox.
            canvas.drawBitmap(scaledShieldEffect, hitbox.left, hitbox.top, null);
            // Optionally, draw a label if desired.
            Paint labelPaint = new Paint();
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(20);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Invincible", hitbox.centerX(), hitbox.top - 10, labelPaint);
        }
    }

    public void increaseScore() {
        score++;
    }

    public int getScore() {
        return score;
    }

    public void checkJumpOnVan(Sprite van) {
        if (this.getBottom() >= van.getY() && this.getBottom() <= van.getY() + 10 &&
                this.getHitbox().right > van.getHitbox().left &&
                this.getHitbox().left < van.getHitbox().right) {
            onVan = true;
        }

        if (onVan && Math.abs(this.getBottom() - van.getY()) < 5) {
            increaseScore();
            onVan = false;
        }
    }

    // Activate the ECO Shield for a specified duration (5 seconds).
    public void activateShield(long duration) {
        shieldActive = true;
        shieldTimer = duration;
    }

    // Getter for checking if the ECO Shield is active.
    public boolean isShieldActive() {
        return shieldActive;
    }
}
