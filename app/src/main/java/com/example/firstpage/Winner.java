package com.example.firstpage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Winner extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_winner);

        TextView winnerText = findViewById(R.id.winnerText);
        Button playAgainButton = findViewById(R.id.playAgainButton);

        int finalScore = getIntent().getIntExtra("FINAL_SCORE", 0);
        winnerText.setText("🎉 You Win! 🎉\nScore: " + finalScore);

        playAgainButton.setOnClickListener(v -> {
            Intent intent = new Intent(Winner.this, DragGame.class);
            startActivity(intent);
            finish();
        });
    }
}