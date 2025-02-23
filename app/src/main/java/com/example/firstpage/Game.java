package com.example.firstpage;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class Game extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Find the Play button in the layout
        Button playButton = findViewById(R.id.playButton1);
        Button playButton2 = findViewById(R.id.playButton2);
        Button playButton3 = findViewById(R.id.playButton3);

        // Set an OnClickListener for the Play button
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to GameView activity
                Intent intent = new Intent(Game.this, Game1Tut.class);
                startActivity(intent);
            }
        });

        playButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to GameView activity
                Intent intent = new Intent(Game.this, DragGame.class);
                startActivity(intent);
            }
        });

        playButton3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to GameView activity
                Intent intent = new Intent(Game.this, BattleEcoActivity.class);
                startActivity(intent);
            }
        });
    }
}
