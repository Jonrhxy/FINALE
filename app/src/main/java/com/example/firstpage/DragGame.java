package com.example.firstpage;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DragGame extends AppCompatActivity {


    private TextView scoreText, livesText, highScoreText;
    private Button finishButton;
    private int score = 0;
    private int highScore = 0;
    private int lives = 3;
    private int currentItemIndex = 0;
    private final int totalItems = 10;
    private final int WINNING_SCORE = 50;
    private LinearLayout itemContainer;
    private LinearLayout highCarbonZone, lowCarbonZone;

    private List<String> selectedItems;
    private final HashMap<String, Integer> itemImages = new HashMap<>();
    private final HashMap<String, String> itemCategory = new HashMap<>();
    private ImageView currentItemView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drag_game);

        scoreText = findViewById(R.id.scoreText);
        livesText = findViewById(R.id.livesText);
        highScoreText = findViewById(R.id.Highscrore);
        itemContainer = findViewById(R.id.itemContainer);
        highCarbonZone = findViewById(R.id.highCarbonZone);
        lowCarbonZone = findViewById(R.id.lowCarbonZone);

        // Load and display high score
        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        highScore = prefs.getInt("HIGH_SCORE", 0);
        updateHighScoreText();

        loadItems();
        selectedItems = new ArrayList<>(itemImages.keySet());
        Collections.shuffle(selectedItems);
        selectedItems = selectedItems.subList(0, totalItems);

        updateLivesText();
        showNextItem();

        setDropListener(highCarbonZone);
        setDropListener(lowCarbonZone);
    }

    private void loadItems() {
        addItem("Car", R.drawable.car, "high");
        addItem("Truck", R.drawable.truck, "high");
        addItem("Motorcycle", R.drawable.motorcycle, "high");
        addItem("Bus", R.drawable.bus, "high");
        addItem("Bicycle", R.drawable.bicycle, "low");
        addItem("Electric Car", R.drawable.electric_car, "low");
        addItem("Beef", R.drawable.beef, "high");
        addItem("Chicken", R.drawable.chicken, "low");
        addItem("Rice", R.drawable.rice, "high");
        addItem("Vegetables", R.drawable.vegetables, "low");
    }

    private void addItem(String name, int imageRes, String category) {
        itemImages.put(name, imageRes);
        itemCategory.put(name, category);
    }

    private void showNextItem() {
        itemContainer.removeAllViews();
        if (currentItemIndex < totalItems) {
            String itemName = selectedItems.get(currentItemIndex);
            currentItemView = new ImageView(this);
            currentItemView.setImageResource(itemImages.get(itemName));
            currentItemView.setTag(itemCategory.get(itemName));
            currentItemView.setLayoutParams(new LinearLayout.LayoutParams(600, 600));
            itemContainer.addView(currentItemView);

            currentItemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    ClipData data = ClipData.newPlainText("", "");
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                    v.startDragAndDrop(data, shadowBuilder, v, 0);
                    v.setVisibility(View.INVISIBLE);
                    return true;
                }
                return false;
            });
        }
    }

    private void setDropListener(LinearLayout dropZone) {
        dropZone.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);

                case DragEvent.ACTION_DROP:
                    View draggedView = (View) event.getLocalState();
                    String itemTag = (String) draggedView.getTag();
                    String zoneTag = (dropZone.getId() == R.id.highCarbonZone) ? "high" : "low";

                    if (itemTag.equals(zoneTag)) {
                        score += 10;
                        dropZone.setBackgroundColor(Color.GREEN);
                    } else {
                        score -= 5;
                        lives--;
                        dropZone.setBackgroundColor(Color.RED);
                        updateLivesText();
                    }

                    scoreText.setText("Score: " + score);
                    currentItemIndex++;

                    new Handler().postDelayed(() -> {
                        dropZone.setBackgroundColor(getResources().getColor(R.color.default_zone_color));
                        if (lives <= 0) {
                            gameOver();
                        } else if (score >= WINNING_SCORE) {
                            winGame();
                        } else {
                            showNextItem();
                        }
                    }, 500);

                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    if (currentItemView != null) {
                        currentItemView.setVisibility(View.VISIBLE);
                    }
                    return true;
            }
            return false;
        });
    }

    private void updateLivesText() {
        livesText.setText("Lives: " + lives);
    }

    private void updateHighScoreText() {
        highScoreText.setText("High Score: " + highScore);
    }

    private void saveHighScore() {
        if (score > highScore) {
            highScore = score;
            SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("HIGH_SCORE", highScore);
            editor.apply();
        }
    }

    private void gameOver() {
        saveHighScore();
        Intent intent = new Intent(DragGame.this, GOver.class);
        intent.putExtra("FINAL_SCORE", score);
        startActivity(intent);
        finish();
    }

    private void winGame() {
        saveHighScore();
        Intent intent = new Intent(DragGame.this, Winner.class);
        intent.putExtra("FINAL_SCORE", score);
        startActivity(intent);
        finish();
    }
}