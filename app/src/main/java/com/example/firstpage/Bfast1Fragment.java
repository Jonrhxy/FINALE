package com.example.firstpage;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bfast1Fragment extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    private ImageView imageView;
    private TextView resultText;
    private Bitmap imageBitmap;

    // Food CO2 Emissions Mapping
    private static final Map<String, Float> foodCO2Map = new HashMap<>();

    static {
        foodCO2Map.put("beef", 27.0f);
        foodCO2Map.put("chicken", 6.9f);
        foodCO2Map.put("pork", 7.6f);
        foodCO2Map.put("rice", 4.5f);
        foodCO2Map.put("vegetable", 2.0f);
        foodCO2Map.put("fruit", 1.1f);
        foodCO2Map.put("cheese", 13.5f);
        foodCO2Map.put("milk", 3.2f);

        // Filipino Meals
        foodCO2Map.put("adobo", 7.5f);
        foodCO2Map.put("sinigang", 6.8f);
        foodCO2Map.put("lechon", 20.0f);
        foodCO2Map.put("longganisa", 8.5f);
        foodCO2Map.put("tapsilog", 10.0f);
        foodCO2Map.put("bulalo", 15.0f);
        foodCO2Map.put("kare-kare", 9.5f);
        foodCO2Map.put("sisig", 12.0f);
        foodCO2Map.put("halo-halo", 3.5f);
        foodCO2Map.put("pancit", 4.0f);
        foodCO2Map.put("lumpia", 3.2f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bfast1_fragment);

        Button captureButton = findViewById(R.id.captureButton);
        Button pickImageButton = findViewById(R.id.pickImageButton);
        imageView = findViewById(R.id.imageView1);
        resultText = findViewById(R.id.resultText);

        captureButton.setOnClickListener(v -> dispatchTakePictureIntent());
        pickImageButton.setOnClickListener(v -> dispatchPickImageIntent());
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void dispatchPickImageIntent() {
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickImageIntent, REQUEST_IMAGE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            try {
                if (requestCode == REQUEST_IMAGE_CAPTURE) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        imageBitmap = (Bitmap) extras.get("data");
                    }
                } else if (requestCode == REQUEST_IMAGE_PICK) {
                    Uri imageUri = data.getData();
                    if (imageUri != null) {
                        try (InputStream imageStream = getContentResolver().openInputStream(imageUri)) {
                            if (imageStream != null) {
                                imageBitmap = BitmapFactory.decodeStream(imageStream);
                            }
                        }
                    } else {
                        resultText.setText("Error: Selected image is null.");
                        return;
                    }
                }

                if (imageBitmap != null) {
                    imageView.setImageBitmap(imageBitmap);
                    processImage();
                } else {
                    resultText.setText("Error: Failed to load image.");
                }

            } catch (IOException e) {
                resultText.setText("Error: " + e.getMessage());
            }
        } else {
            resultText.setText("No image selected.");
        }
    }

    private void processImage() {
        if (imageBitmap == null) {
            resultText.setText("Error: No image available.");
            return;
        }

        InputImage image = InputImage.fromBitmap(imageBitmap, 0);
        com.google.mlkit.vision.label.ImageLabeler labeler =
                ImageLabeling.getClient(new ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.6f) // Adjust for better accuracy
                        .build());

        labeler.process(image)
                .addOnSuccessListener(this::filterFoodLabels)
                .addOnFailureListener(e -> resultText.setText("Error: " + e.getMessage()));
    }

    private void filterFoodLabels(List<ImageLabel> labels) {
        StringBuilder detectedLabelsText = new StringBuilder("🔍 Detected Labels:\n");
        StringBuilder results = new StringBuilder();
        boolean foodDetected = false;

        // Rice variations for better matching
        String[] riceLabels = {"rice", "white rice", "steamed rice", "cooked rice", "boiled rice"};
        String[] foodKeywords = {"food", "meal", "dish", "cuisine", "snack", "fruit", "vegetable", "drink"};

        for (ImageLabel label : labels) {
            String detectedLabel = label.getText().toLowerCase().trim();
            float confidence = label.getConfidence();

            detectedLabelsText.append("🔹 ").append(label.getText())
                    .append(" (").append(String.format("%.2f", confidence)).append(")\n");

            for (String foodName : foodCO2Map.keySet()) {
                if (detectedLabel.contains(foodName) || foodName.contains(detectedLabel)) {
                    foodDetected = true;
                    results.append("🍽 Found: ").append(label.getText()).append("\n")
                            .append("✅ Confidence: ").append(String.format("%.2f", confidence)).append("\n")
                            .append("🌍 CO₂ Emission: ").append(foodCO2Map.get(foodName)).append(" kg CO₂/kg\n\n");
                }
            }

            for (String rice : riceLabels) {
                if (detectedLabel.contains(rice)) {
                    foodDetected = true;
                    results.append("🍚 Found: Rice\n")
                            .append("✅ Confidence: ").append(String.format("%.2f", confidence)).append("\n")
                            .append("🌍 CO₂ Emission: ").append(foodCO2Map.get("rice")).append(" kg CO₂/kg\n\n");
                }
            }

            for (String keyword : foodKeywords) {
                if (detectedLabel.contains(keyword)) {
                    foodDetected = true;
                    break;
                }
            }
        }

        resultText.setText(detectedLabelsText.toString());

        if (foodDetected) {
            resultText.setText(resultText.getText() + "\n\n🍽 FOOD FOUND! 🌍\n\n" + results);
        } else {
            resultText.setText(resultText.getText() + "\n\n🚫 No food detected. Try another image.");
        }
    }
}