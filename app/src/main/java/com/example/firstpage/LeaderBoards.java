package com.example.firstpage;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LeaderBoards extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView firstPlaceName, firstPlaceScore;
    private TextView secondPlaceName, secondPlaceScore;
    private TextView thirdPlaceName, thirdPlaceScore;
    private LinearLayout remainingLeaderboardContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leader_boards);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Bind UI elements
        firstPlaceName = findViewById(R.id.first_place_name);
        firstPlaceScore = findViewById(R.id.first_place_rank);
        secondPlaceName = findViewById(R.id.second_place_name);
        secondPlaceScore = findViewById(R.id.second_place_rank);
        thirdPlaceName = findViewById(R.id.third_place_name);
        thirdPlaceScore = findViewById(R.id.third_place_rank);
        remainingLeaderboardContainer = findViewById(R.id.remaining_leaderboard_container);

        // Load leaderboard data
        loadLeaderBoardData();
    }

    // Load and sort leaderboard data based on highScore
    private void loadLeaderBoardData() {
        db.collection("Games")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<GameData> leaderboardData = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            if (document.contains("highScore")) { // Use highScore instead of points
                                GameData gameData = new GameData(
                                        document.getId(),
                                        document.getLong("highScore").intValue()
                                );
                                leaderboardData.add(gameData);
                            }
                        }
                        // Sort by highScore (highest first)
                        leaderboardData.sort((a, b) -> Integer.compare(b.highScore, a.highScore));
                        updateLeaderboardUI(leaderboardData);
                    } else {
                        System.err.println("Error getting documents: " + task.getException());
                    }
                });
    }

    // Update Firestore only if the new score is higher than current highScore
    public void updateUserScore(String username, int newScore) {
        db.collection("Games").document(username)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.contains("highScore")) {
                        int currentHighScore = document.getLong("highScore").intValue();
                        if (newScore > currentHighScore) {
                            // Update only if the new score is higher than highScore
                            db.collection("Games").document(username)
                                    .update("highScore", newScore)
                                    .addOnSuccessListener(aVoid -> System.out.println("High score updated successfully"))
                                    .addOnFailureListener(e -> System.err.println("Error updating high score: " + e.getMessage()));
                        }
                    } else {
                        // Add new record if user does not exist
                        db.collection("Games").document(username)
                                .set(new GameData(username, newScore))
                                .addOnSuccessListener(aVoid -> System.out.println("New high score added successfully"))
                                .addOnFailureListener(e -> System.err.println("Error adding new high score: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> System.err.println("Error fetching document: " + e.getMessage()));
    }

    // Update leaderboard UI
    private void updateLeaderboardUI(List<GameData> leaderboardData) {
        remainingLeaderboardContainer.removeAllViews(); // Clear previous views

        for (int i = 0; i < leaderboardData.size(); i++) {
            GameData player = leaderboardData.get(i);

            // Assign top 3 players to special slots
            if (i == 0) {
                firstPlaceName.setText(player.username);
                firstPlaceScore.setText(player.highScore + " HIGH SCORE");
            } else if (i == 1) {
                secondPlaceName.setText(player.username);
                secondPlaceScore.setText(player.highScore + " HIGH SCORE");
            } else if (i == 2) {
                thirdPlaceName.setText(player.username);
                thirdPlaceScore.setText(player.highScore + " HIGH SCORE");
            }

            // Create a new layout for the ranking list (including Top 1-3)
            LinearLayout playerLayout = new LinearLayout(this);
            playerLayout.setOrientation(LinearLayout.HORIZONTAL);
            playerLayout.setPadding(16, 8, 16, 8);
            playerLayout.setBackgroundColor(Color.parseColor(i % 2 == 0 ? "#DFF8E7" : "#C8E6C9")); // Alternating row colors
            playerLayout.setGravity(Gravity.CENTER_VERTICAL);
            playerLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // TextView for rank + name
            TextView playerName = new TextView(this);
            playerName.setText((i + 1) + ". " + player.username);
            playerName.setTextSize(16);
            playerName.setTextColor(Color.BLACK);
            playerName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            // TextView for score
            TextView playerPoints = new TextView(this);
            playerPoints.setText(player.highScore + " HIGH SCORE");
            playerPoints.setTextSize(14);
            playerPoints.setTextColor(Color.DKGRAY);

            // Add TextViews to the layout
            playerLayout.addView(playerName);
            playerLayout.addView(playerPoints);

            // Add to the remaining leaderboard container
            remainingLeaderboardContainer.addView(playerLayout);
        }
    }

    // Data model for leaderboard
    public static class GameData {
        public String username;
        public int highScore; // Updated field

        public GameData() {
            // Default constructor
        }

        public GameData(String username, int highScore) {
            this.username = username;
            this.highScore = highScore;
        }
    }
}