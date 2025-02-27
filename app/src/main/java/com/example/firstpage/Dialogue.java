package com.example.firstpage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

public class Dialogue {
    private Rect bubbleRect;
    private Rect screenRect;  // The full-screen rectangle.
    private long countdown;   // in milliseconds
    private String line1;
    private String line2;
    private Context context;
    private boolean started = false; // Countdown begins when tapped

    // Constructor now takes the full-screen rectangle as well.
    public Dialogue(Context context, Rect bubbleRect, Rect screenRect, long countdown, String line1, String line2) {
        this.context = context;
        this.bubbleRect = bubbleRect;
        this.screenRect = screenRect;
        this.countdown = countdown;
        this.line1 = line1;
        this.line2 = line2;
    }

    // Call this method when the dialogue bubble is tapped.
    public void startCountdown() {
        started = true;
    }

    public long getCountdown() {
        return countdown;
    }

    public void update(long elapsed) {
        if (started) {
            countdown -= elapsed;
            if (countdown < 0) {
                countdown = 0;
            }
        }
    }

    public void draw(Canvas canvas) {
        // If countdown is finished, vanish the dialogue abruptly.
        if (countdown <= 0) {
            return;
        }

        // Draw a rounded rectangle as the dialogue bubble background with smooth (rounded) corners.
        Paint bubbleBg = new Paint();
        bubbleBg.setColor(Color.WHITE);
        RectF bubbleRectF = new RectF(bubbleRect);
        canvas.drawRoundRect(bubbleRectF, 20, 20, bubbleBg);

        // Draw dialogue text in bold, capitalized, and black.
        Paint dialoguePaint = new Paint();
        dialoguePaint.setColor(Color.BLACK);
        dialoguePaint.setTextSize(40);
        dialoguePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        dialoguePaint.setTextAlign(Paint.Align.CENTER);
        int bubbleCenterX = bubbleRect.centerX();
        int bubbleCenterY = bubbleRect.centerY();
        canvas.drawText(line1.toUpperCase(), bubbleCenterX, bubbleCenterY - 30, dialoguePaint);
        canvas.drawText(line2.toUpperCase(), bubbleCenterX, bubbleCenterY + 10, dialoguePaint);

        // Draw a larger countdown circle at the center of the screen.
        Paint circlePaint = new Paint();
        circlePaint.setColor(Color.GREEN);
        int circleX = screenRect.centerX();
        int circleY = screenRect.centerY();
        int circleRadius = 70;  // Bigger circle.
        canvas.drawCircle(circleX, circleY, circleRadius, circlePaint);

        // Draw the countdown number centered inside the circle.
        Paint countdownPaint = new Paint();
        countdownPaint.setColor(Color.WHITE);
        countdownPaint.setTextSize(60);  // Larger font.
        countdownPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        countdownPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = countdownPaint.getFontMetrics();
        float textOffset = (fm.ascent + fm.descent) / 2;
        int secondsLeft = (int) Math.ceil(countdown / 1000.0);
        canvas.drawText(String.valueOf(secondsLeft), circleX, circleY - textOffset, countdownPaint);
    }

    // Process touch events: if the bubble is tapped, start the countdown.
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Check if the tap is within the dialogue bubble.
            if (bubbleRect.contains((int) event.getX(), (int) event.getY())) {
                startCountdown();
                return true;
            }
        }
        return false;
    }
}
