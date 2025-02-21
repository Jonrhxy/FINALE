package com.example.firstpage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class Quiz2of2 extends AppCompatActivity {

    // Variable to track the score
    private int score;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quiz2of2);

        // Retrieve the score passed from the previous activity
        score = getIntent().getIntExtra("score", 0); // Default score is 0 if not passed

        // Find the RadioGroup
        RadioGroup radioGroup = findViewById(R.id.qz2q2chcs);

        // Set listener for RadioGroup selection
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1) { // Ensure a valid selection
                // Check if the correct answer is selected (btnq2A)
                if (checkedId == R.id.btnq2of2A) { // Replace btnq2A with the actual button ID for question 2
                    score++; // Increment the score for the correct answer
                }

                // Proceed to the next question or finish quiz
                Intent intent = new Intent(Quiz2of2.this, Quiz3of2.class);
                // Pass the updated score to the next activity
                intent.putExtra("score", score);
                startActivity(intent);
            }
        });
    }
}
