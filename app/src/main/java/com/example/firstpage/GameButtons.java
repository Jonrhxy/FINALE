package com.example.firstpage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

public class GameButtons {
    private Bitmap pauseBtn, playBtn, nextBtn, tryBtn;
    private Rect pauseRect, playRect, nextRect, tryRect;
    private boolean paused = false;
    private Context context;
    private Rect screen;

    public GameButtons(Context context, Rect screen) {
        this.context = context;
        this.screen = screen;
        // Load button images from your drawable resources.
        pauseBtn = BitmapFactory.decodeResource(context.getResources(), R.drawable.pause, null);
        playBtn = BitmapFactory.decodeResource(context.getResources(), R.drawable.play, null);
        nextBtn = BitmapFactory.decodeResource(context.getResources(), R.drawable.next, null);
        // Rename your try image resource to try_btn.png (avoid reserved keywords).
        tryBtn = BitmapFactory.decodeResource(context.getResources(), R.drawable.try_btn, null);

        // Define a default button size.
        int btnSize = screen.width() / 10;

        // Pause/Play button positioned at the top-right.
        pauseRect = new Rect(screen.width() - btnSize - 20, 20, screen.width() - 20, 20 + btnSize);
        playRect = pauseRect; // Same location

        // Default next and try button rectangles.
        nextRect = new Rect(screen.width()/2 - btnSize/2, screen.height()/2 + 100,
                screen.width()/2 + btnSize/2, screen.height()/2 + 100 + btnSize);
        tryRect = new Rect(screen.width()/2 - btnSize/2, screen.height()/2 + 100,
                screen.width()/2 + btnSize/2, screen.height()/2 + 100 + btnSize);
    }

    // Draw UI buttons.
    // In end states (LEVEL_COMPLETE or LOST) only the next (or try) button is drawn.
    public void draw(Canvas canvas, Game1.GameState state) {
        if (state == Game1.GameState.RUNNING || state == Game1.GameState.START) {
            // In running mode, always show pause/play button.
            if (paused) {
                canvas.drawBitmap(playBtn, null, playRect, null);
            } else {
                canvas.drawBitmap(pauseBtn, null, pauseRect, null);
            }
        } else if (state == Game1.GameState.LEVEL_COMPLETE) {
            canvas.drawBitmap(nextBtn, null, nextRect, null);
        } else if (state == Game1.GameState.LOST) {
            canvas.drawBitmap(tryBtn, null, tryRect, null);
        }
    }

    /**
     * Checks if a touch at (x,y) hits any button.
     * Returns:
     *   1 = pause/play button hit (toggle pause)
     *   2 = next button hit (proceed to next level)
     *   3 = try button hit (restart current level)
     *   0 = no button hit.
     */
    public int checkTouch(int x, int y, Game1.GameState state) {
        // In running state, only check pause/play.
        if (state == Game1.GameState.RUNNING || state == Game1.GameState.START) {
            if (pauseRect.contains(x, y)) {
                paused = !paused;
                return 1;
            }
        } else if (state == Game1.GameState.LEVEL_COMPLETE) {
            if (nextRect.contains(x, y)) {
                return 2;
            }
        } else if (state == Game1.GameState.LOST) {
            if (tryRect.contains(x, y)) {
                return 3;
            }
        }
        return 0;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean p) {
        paused = p;
    }

    // Update the try button's rectangle.
    public void setTryButtonRect(Rect newRect) {
        this.tryRect = newRect;
    }

    // Update the next button's rectangle.
    public void setNextButtonRect(Rect newRect) {
        this.nextRect = newRect;
    }
}
