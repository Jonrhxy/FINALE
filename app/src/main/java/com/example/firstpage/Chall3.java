package com.example.firstpage;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Chall3 extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private ImageView cameraIcon, checkMark;
    private Button captureButton;
    private Bitmap capturedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chall3);

        cameraIcon = findViewById(R.id.cameraIcon);
        checkMark = findViewById(R.id.checkMark);
        captureButton = findViewById(R.id.captureButton);

        checkChallengeStatus(); // Check if the challenge is already done for today

        captureButton.setOnClickListener(v -> openCamera());
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            capturedImage = (Bitmap) data.getExtras().get("data");

            if (capturedImage != null) {
                processImage();
            }
        }
    }

    private void processImage() {
        if (capturedImage == null) {
            Toast.makeText(this, "Error: No image available.", Toast.LENGTH_SHORT).show();
            return;
        }

        InputImage image = InputImage.fromBitmap(capturedImage, 0);
        com.google.mlkit.vision.label.ImageLabeler labeler =
                ImageLabeling.getClient(new ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.7f)
                        .build());

        labeler.process(image)
                .addOnSuccessListener(this::filterFoodLabels)
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void filterFoodLabels(List<ImageLabel> labels) {
        boolean foodDetected = false;

        for (ImageLabel label : labels) {
            String labelText = label.getText().toLowerCase();
            if (labelText.contains("food") || labelText.contains("meal") || labelText.contains("dish")) {
                foodDetected = true;
                break;
            }
        }

        if (foodDetected) {
            markChallengeAsDone();
        } else {
            markChallengeAsNotDone();
        }
    }

    private void markChallengeAsDone() {
        checkMark.setVisibility(View.VISIBLE);
        saveChallengeCompletion();
        Toast.makeText(this, "Challenge completed!", Toast.LENGTH_SHORT).show();
    }

    private void markChallengeAsNotDone() {
        checkMark.setVisibility(View.GONE);
        clearChallengeCompletion();
        Toast.makeText(this, "No food detected. Challenge not completed yet.", Toast.LENGTH_SHORT).show();
    }

    private void saveChallengeCompletion() {
        SharedPreferences prefs = getSharedPreferences("ChallengePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        editor.putString("lastCompletedDate", today);
        editor.apply();
    }

    private void clearChallengeCompletion() {
        SharedPreferences prefs = getSharedPreferences("ChallengePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("lastCompletedDate");
        editor.apply();
    }

    private void checkChallengeStatus() {
        SharedPreferences prefs = getSharedPreferences("ChallengePrefs", MODE_PRIVATE);
        String lastCompletedDate = prefs.getString("lastCompletedDate", "");

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().getTime());

        if (today.equals(lastCompletedDate)) {
            checkMark.setVisibility(View.VISIBLE);
        } else {
            checkMark.setVisibility(View.GONE);
        }
    }
}