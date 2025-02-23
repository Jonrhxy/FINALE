package com.example.firstpage;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bfast1Fragment extends AppCompatActivity {
    private static final String TAG = "MLKitDebug";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    private ImageView imageView;
    private TextView resultText;
    private Bitmap imageBitmap;

    // Extended food categories with their estimated CO2 emissions (in kg CO2 per kg).
    private static final Map<String, Float> foodCO2Map = Map.ofEntries(
            Map.entry("beef", 27.0f),
            Map.entry("chicken", 6.9f),
            Map.entry("pork", 7.6f),
            Map.entry("rice", 4.5f),
            Map.entry("vegetable", 2.0f),
            Map.entry("fruit", 1.1f),
            Map.entry("cheese", 13.5f),
            Map.entry("milk", 3.2f),
            Map.entry("lamb", 39.2f),
            Map.entry("turkey", 10.0f),
            Map.entry("fish", 6.0f),
            Map.entry("egg", 4.8f),
            Map.entry("potato", 2.9f),
            Map.entry("bread", 1.2f),
            Map.entry("pasta", 1.3f),
            Map.entry("nuts", 0.7f),
            Map.entry("soy", 2.0f),
            Map.entry("tofu", 2.0f)
    );

    // Extended synonyms mapping to catch alternative or more specific label names.
    private static final Map<String, String> foodSynonymsMap = Map.ofEntries(
            Map.entry("steak", "beef"),
            Map.entry("roast beef", "beef"),
            Map.entry("minced beef", "beef"),
            Map.entry("pork chop", "pork"),
            Map.entry("ham", "pork"),
            Map.entry("bacon", "pork"),
            Map.entry("roast chicken", "chicken"),
            Map.entry("chicken thigh", "chicken"),
            Map.entry("chicken breast", "chicken"),
            Map.entry("lamb chop", "lamb"),
            Map.entry("turkey breast", "turkey"),
            Map.entry("salmon", "fish"),
            Map.entry("tuna", "fish"),
            Map.entry("cod", "fish"),
            Map.entry("eggs", "egg"),
            Map.entry("sweet potato", "potato"),
            Map.entry("whole grain bread", "bread"),
            Map.entry("spaghetti", "pasta"),
            Map.entry("almonds", "nuts"),
            Map.entry("cashews", "nuts"),
            Map.entry("soy milk", "soy"),
            Map.entry("edamame", "soy"),
            Map.entry("bean curd", "tofu")
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bfast1_fragment);

        final Button captureButton = findViewById(R.id.captureButton);
        final Button pickImageButton = findViewById(R.id.pickImageButton);
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
                        .setConfidenceThreshold(0.7f)  // Adjust threshold if necessary.
                        .build());

        labeler.process(image)
                .addOnSuccessListener(this::filterFoodLabels)
                .addOnFailureListener(e -> resultText.setText("Error: " + e.getMessage()));
    }

    private void filterFoodLabels(List<ImageLabel> labels) {
        // Use a map to record the best (highest-confidence) match for each food category.
        Map<String, Float> bestMatches = new HashMap<>();

        // Log all detected labels for debugging.
        for (ImageLabel label : labels) {
            Log.d(TAG, "Detected label: " + label.getText() + " (Confidence: " + label.getConfidence() + ")");
            float confidence = label.getConfidence();
            String normalizedLabel = label.getText().toLowerCase();
            String matchedFood = null;

            // Check for a direct match in foodCO2Map.
            for (String foodKey : foodCO2Map.keySet()) {
                if (normalizedLabel.contains(foodKey)) {
                    matchedFood = foodKey;
                    break;
                }
            }

            // If no direct match, check against synonyms.
            if (matchedFood == null) {
                for (Map.Entry<String, String> entry : foodSynonymsMap.entrySet()) {
                    if (normalizedLabel.contains(entry.getKey())) {
                        matchedFood = entry.getValue();
                        break;
                    }
                }
            }

            // Update bestMatches if a food category is found.
            if (matchedFood != null) {
                if (!bestMatches.containsKey(matchedFood) || confidence > bestMatches.get(matchedFood)) {
                    bestMatches.put(matchedFood, confidence);
                }
            }
        }

        // If multiple food categories are detected, sort them by confidence.
        if (!bestMatches.isEmpty()) {
            List<Map.Entry<String, Float>> sortedMatches = new ArrayList<>(bestMatches.entrySet());
            sortedMatches.sort((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()));

            StringBuilder results = new StringBuilder();
            for (Map.Entry<String, Float> entry : sortedMatches) {
                results.append(entry.getKey())
                        .append(" - Estimated CO2 Emission: ")
                        .append(foodCO2Map.get(entry.getKey())).append(" kg CO2/kg\n");
            }
            resultText.setText(results.toString());
        } else {
            resultText.setText("No food detected.");
        }
    }
}