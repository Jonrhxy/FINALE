package com.example.firstpage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class GOver extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_gover);
        Button restartButton = findViewById(R.id.restartButton);
        restartButton.setOnClickListener(v -> {
            Intent intent = new Intent(GOver.this, DragGame.class);
            startActivity(intent);
            finish();
        });
    }
}