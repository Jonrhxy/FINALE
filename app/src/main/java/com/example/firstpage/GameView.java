package com.example.firstpage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GameView extends View {
    String message = "";
    boolean showMessage = false;
    long messageStartTime;

    int dWidth, dHeight;
    Bitmap background, heartImage;
    Bitmap guideImage, bubbleTextImage; // For guide_character & bubble_text
    Bitmap[] healthyFoods, harmfulFoods;
    Bitmap[] currentFoods; // Array to hold multiple food items
    Handler handler;
    Runnable runnable;

    long UPDATE_MILLIS = 30;
    int[] foodX, foodY; // Arrays to hold multiple food positions
    Random random;
    int points = 0;
    float TEXT_SIZE = 50;
    Paint textPaint, messagePaint;
    int life = 3;
    Context context;
    int foodSpeed;
    boolean[] isHealthy; // Array to determine if each food item is healthy or harmful
    MediaPlayer mpPoint, mpLoseLife;

    SharedPreferences sharedPreferences;
    int highScore;

    // Firebase Firestore instance
    FirebaseFirestore db;
    String username;

    String storylineMessage = "";
    long messageTime = 0;
    private static final long ANIM_DURATION = 1000; // 1 second for the typewriter effect

    public GameView(Context context) {
        super(context);
        this.context = context;

        Display display = ((Activity) getContext()).getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        dWidth = size.x;
        dHeight = size.y;

        sharedPreferences = context.getSharedPreferences("GamePreferences", Context.MODE_PRIVATE);
        highScore = sharedPreferences.getInt("highScore", 0);

        db = FirebaseFirestore.getInstance();
        username = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();

        background = BitmapFactory.decodeResource(getResources(), R.drawable.background);
        background = Bitmap.createScaledBitmap(background, dWidth, dHeight, false);

        heartImage = BitmapFactory.decodeResource(getResources(), R.drawable.heart);
        heartImage = Bitmap.createScaledBitmap(heartImage, 100, 100, false);

        // Load guide_character and bubble_text images, then scale them bigger
        Bitmap rawGuide = BitmapFactory.decodeResource(getResources(), R.drawable.guide_character);
        guideImage = Bitmap.createScaledBitmap(
                rawGuide,
                (int)(dWidth * 0.4f),     // 40% of screen width
                (int)(dHeight * 0.4f),    // 40% of screen height
                false
        );

        Bitmap rawBubble = BitmapFactory.decodeResource(getResources(), R.drawable.bubble_text);
        bubbleTextImage = Bitmap.createScaledBitmap(
                rawBubble,
                (int)(dWidth * 0.6f),     // 60% of screen width
                (int)(dHeight * 0.25f),   // 25% of screen height
                false
        );

        healthyFoods = new Bitmap[]{
                BitmapFactory.decodeResource(getResources(), R.drawable.apple),
                BitmapFactory.decodeResource(getResources(), R.drawable.broccoli),
                BitmapFactory.decodeResource(getResources(), R.drawable.carrot)
        };

        harmfulFoods = new Bitmap[]{
                BitmapFactory.decodeResource(getResources(), R.drawable.donut),
                BitmapFactory.decodeResource(getResources(), R.drawable.fries),
                BitmapFactory.decodeResource(getResources(), R.drawable.hamburger)
        };

        handler = new Handler();
        runnable = this::invalidate;
        random = new Random();

        resetFood();

        textPaint = new Paint();
        textPaint.setTextSize(TEXT_SIZE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(0xFFFFFFFF);

        // Initialize messagePaint to avoid NullPointerException
        messagePaint = new Paint();
        messagePaint.setTextSize(TEXT_SIZE);
        messagePaint.setTextAlign(Paint.Align.CENTER);
        messagePaint.setColor(0xFFFFFFFF);

        mpPoint = MediaPlayer.create(context, R.raw.point);
        mpLoseLife = MediaPlayer.create(context, R.raw.pop);
    }

    private void resetFood() {
        int numberOfFoods = 2; // Adjust this to control how many food items appear

        foodX = new int[numberOfFoods];
        foodY = new int[numberOfFoods];
        isHealthy = new boolean[numberOfFoods];
        currentFoods = new Bitmap[numberOfFoods];

        for (int i = 0; i < numberOfFoods; i++) {
            foodX[i] = random.nextInt(dWidth - 100);
            foodY[i] = 0;
            isHealthy[i] = random.nextBoolean();
            foodSpeed = 15 + random.nextInt(10);
            currentFoods[i] = isHealthy[i]
                    ? healthyFoods[random.nextInt(healthyFoods.length)]
                    : harmfulFoods[random.nextInt(harmfulFoods.length)];
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(background, 0, 0, null);

        for (int i = 0; i < foodX.length; i++) {
            foodY[i] += foodSpeed;

            if (foodY[i] >= dHeight) {
                if (isHealthy[i]) {
                    life--;
                    if (mpLoseLife != null) mpLoseLife.start();
                    if (life == 0) {
                        updateHighScore();
                        // No additional firebase update here since we update on every score increment
                        Intent intent = new Intent(context, GameOver.class);
                        intent.putExtra("points", points);
                        context.startActivity(intent);
                        ((Activity) context).finish();
                    }
                }
                foodX[i] = random.nextInt(dWidth - 100);
                foodY[i] = 0;
                isHealthy[i] = random.nextBoolean();
                currentFoods[i] = isHealthy[i]
                        ? healthyFoods[random.nextInt(healthyFoods.length)]
                        : harmfulFoods[random.nextInt(harmfulFoods.length)];
            }

            canvas.drawBitmap(currentFoods[i], foodX[i], foodY[i], null);
        }

        // Score and High Score UI with improved visuals
        Paint scorePaint = new Paint();
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(TEXT_SIZE * 1.2f);
        scorePaint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        scorePaint.setShadowLayer(5, 2, 2, Color.BLACK);

        Paint highScorePaint = new Paint(scorePaint);
        highScorePaint.setColor(Color.GREEN);

        canvas.drawText("Score: " + points, 40, TEXT_SIZE + 20, scorePaint);
        canvas.drawText("High Score: " + highScore, 40, TEXT_SIZE * 2 + 40, highScorePaint);

        // Display educational messages at specific score milestones
        if (points == 10) {
            message = "Great start! Eating plant-based food helps lower your carbon footprint.";
            showMessage = true;
            messageStartTime = System.currentTimeMillis();
        } else if (points == 20) {
            message = "Awesome! Reducing food waste also helps cut carbon emissions.";
            showMessage = true;
            messageStartTime = System.currentTimeMillis();
        } else if (points == 30) {
            message = "Keep going! Walking or biking instead of driving saves energy and reduces emissions.";
            showMessage = true;
            messageStartTime = System.currentTimeMillis();
        } else if (points == 40) {
            message = "Fantastic! Choosing local and seasonal foods lowers transportation emissions.";
            showMessage = true;
            messageStartTime = System.currentTimeMillis();
        }

        // When a milestone message is to be shown, draw the guide with its speech bubble with fade-in/out effect
        if (showMessage && System.currentTimeMillis() - messageStartTime < 3000) {
            long elapsed = System.currentTimeMillis() - messageStartTime;
            int alpha = 255;
            if (elapsed < 500) {
                alpha = (int) ((elapsed / 500f) * 255);
            } else if (elapsed > 2500) {
                alpha = (int) (((3000 - elapsed) / 500f) * 255);
            }
            Paint paintAlpha = new Paint();
            paintAlpha.setAlpha(alpha);

            // Position the guide character on the bottom-right with a margin
            int margin = 30;
            int guideX = dWidth - guideImage.getWidth() - margin;
            int guideY = dHeight - guideImage.getHeight() - margin;

            // Place the bubble to the LEFT of the guide
            int bubbleOffsetX = 50;  // tweak horizontally
            int bubbleOffsetY = 30;  // tweak vertically
            int bubbleX = guideX - bubbleTextImage.getWidth() + bubbleOffsetX;
            int bubbleY = guideY - bubbleTextImage.getHeight() + bubbleOffsetY;

            // Draw the bubble and guide with current alpha
            canvas.drawBitmap(bubbleTextImage, bubbleX, bubbleY, paintAlpha);
            canvas.drawBitmap(guideImage, guideX, guideY, paintAlpha);

            // Set up text paint for drawing inside the bubble
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(TEXT_SIZE - 10);
            textPaint.setAlpha(alpha);

            // Give a bit of padding inside the bubble
            int bubblePadding = 20;
            int maxTextWidth = bubbleTextImage.getWidth() - 2 * bubblePadding;

            // --- Begin Typewriter Animation Section ---
            int totalChars = message.length();
            int charsToShow = (int) (totalChars * Math.min(1f, elapsed / (float) ANIM_DURATION));
            String animatedText = message.substring(0, charsToShow);
            // --- End Typewriter Animation Section ---

            // Word wrapping
            String[] words = animatedText.split(" ");
            StringBuilder wrappedText = new StringBuilder();
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (textPaint.measureText(line + " " + word) < maxTextWidth) {
                    line.append(" ").append(word);
                } else {
                    wrappedText.append(line).append("\n");
                    line = new StringBuilder(word);
                }
            }
            wrappedText.append(line);
            String[] lines = wrappedText.toString().split("\n");

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float lineHeight = fm.descent - fm.ascent;
            float totalTextHeight = lines.length * lineHeight;
            float textStartY = bubbleY + (bubbleTextImage.getHeight() - totalTextHeight) / 2 - fm.ascent;

            for (String l : lines) {
                String trimmedLine = l.trim();
                float lineWidth = textPaint.measureText(trimmedLine);
                float textX = bubbleX + bubblePadding + (maxTextWidth - lineWidth) / 2;
                canvas.drawText(trimmedLine, textX, textStartY, textPaint);
                textStartY += lineHeight;
            }
        }

        // Draw hearts for lives
        for (int i = 0; i < life; i++) {
            canvas.drawBitmap(heartImage, dWidth - 120 - (i * 90), 30, null);
        }

        if (life != 0) handler.postDelayed(runnable, UPDATE_MILLIS);
    }

    private void checkStorylineMilestone() {
        if (points % 10 == 0 && points > 0) {
            storylineMessage = "Eating plant-based food reduces your carbon footprint!";
            showMessage = true;
            messageTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float touchX = event.getX();
                float touchY = event.getY();

                for (int i = 0; i < foodX.length; i++) {
                    int foodWidth = currentFoods[i].getWidth();
                    int foodHeight = currentFoods[i].getHeight();

                    if (touchX >= foodX[i] && touchX <= (foodX[i] + foodWidth) &&
                            touchY >= foodY[i] && touchY <= (foodY[i] + foodHeight)) {
                        if (isHealthy[i]) {
                            checkStorylineMilestone();
                            points++;
                            // Update Firebase highScore field by incrementing by 1
                            updateFirebaseHighScoreIncrement(1);
                            if (points % 10 == 0) {
                                message = "Eating plant-based food reduces your carbon footprint!";
                                showMessage = true;
                                messageStartTime = System.currentTimeMillis();
                            }
                        } else {
                            life--;
                            if (mpLoseLife != null) mpLoseLife.start();
                            if (life == 0) {
                                updateHighScore();
                                // No need to call saveGameDataToFirestore() here since we update incrementally
                                Intent intent = new Intent(context, GameOver.class);
                                intent.putExtra("points", points);
                                context.startActivity(intent);
                                ((Activity) context).finish();
                            }
                        }
                        // Reset this specific food item
                        foodX[i] = random.nextInt(dWidth - 100);
                        foodY[i] = 0;
                        isHealthy[i] = random.nextBoolean();
                        currentFoods[i] = isHealthy[i]
                                ? healthyFoods[random.nextInt(healthyFoods.length)]
                                : harmfulFoods[random.nextInt(harmfulFoods.length)];
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public void updateHighScore() {
        if (points > highScore) {
            highScore = points;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("highScore", highScore);
            editor.apply();
        }
    }

    /**
     * Increment the Firebase "highScore" field by a specified amount.
     * This update is sent every time the user earns at least one point,
     * and it does not change your in-game (local) high score.
     */
    private void updateFirebaseHighScoreIncrement(int increment) {
        DocumentReference gameRef = db.collection("Games").document(username);
        Map<String, Object> updates = new HashMap<>();
        updates.put("highScore", FieldValue.increment(increment));
        gameRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    System.out.println("Firebase highScore incremented by " + increment);
                })
                .addOnFailureListener(e -> {
                    System.err.println("Error updating Firebase highScore: " + e.getMessage());
                });
    }
}
