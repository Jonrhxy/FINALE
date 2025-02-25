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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Bfast1Fragment extends AppCompatActivity {
    private static final String TAG = "MLKitDebug";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    private ImageView imageView;
    private TextView resultText;
    private Bitmap imageBitmap;

    // Extended food categories with their estimated CO2 emissions (in kg CO2 per kg).
    // Note: Using Java 9+ style Map.ofEntries might require additional setup. If you get errors,
    // switch to a HashMap or similar approach.
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
                        .setConfidenceThreshold(0.7f)  // Adjust threshold if needed
                        .build());

        labeler.process(image)
                .addOnSuccessListener(this::filterFoodLabels)
                .addOnFailureListener(e -> resultText.setText("Error: " + e.getMessage()));
    }

    /**
     * Process the detected labels, find food categories, sum their carbon footprint,
     * display them, and update Firestore daily doc in food_sources/{displayName}/daily/{yyyy-MM-dd}.
     */
    private void filterFoodLabels(List<com.google.mlkit.vision.label.ImageLabel> labels) {
        // Keep track of the best (highest-confidence) match for each recognized category
        Map<String, Float> bestMatches = new HashMap<>();

        for (com.google.mlkit.vision.label.ImageLabel label : labels) {
            float confidence = label.getConfidence();
            String normalizedLabel = label.getText().toLowerCase();
            String matchedFood = null;

            // Direct match in foodCO2Map
            for (String foodKey : foodCO2Map.keySet()) {
                if (normalizedLabel.contains(foodKey)) {
                    matchedFood = foodKey;
                    break;
                }
            }

            // If no direct match, check synonyms
            if (matchedFood == null) {
                for (Map.Entry<String, String> entry : foodSynonymsMap.entrySet()) {
                    if (normalizedLabel.contains(entry.getKey())) {
                        matchedFood = entry.getValue();
                        break;
                    }
                }
            }

            // Update bestMatches if found
            if (matchedFood != null) {
                if (!bestMatches.containsKey(matchedFood) || confidence > bestMatches.get(matchedFood)) {
                    bestMatches.put(matchedFood, confidence);
                }
            }
        }

        if (!bestMatches.isEmpty()) {
            // Sort by confidence descending
            List<Map.Entry<String, Float>> sortedMatches = new ArrayList<>(bestMatches.entrySet());
            sortedMatches.sort((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()));

            // Build results text and sum carbon
            StringBuilder results = new StringBuilder();
            float totalFoodCarbon = 0f;
            for (Map.Entry<String, Float> entry : sortedMatches) {
                String foodKey = entry.getKey();
                float carbonVal = foodCO2Map.getOrDefault(foodKey, 0f);
                results.append(foodKey)
                        .append(" - Estimated CO2 Emission: ")
                        .append(carbonVal).append(" kg CO2/kg\n");
                totalFoodCarbon += carbonVal;
            }

            // Show results in the UI
            resultText.setText(results.toString());

            // Update Firestore daily doc with the sum of carbon for recognized items
            updateFoodFirestore(totalFoodCarbon);

        } else {
            resultText.setText("No food detected.");
        }
    }

    /**
     * Updates the daily doc in Firestore at:
     *   food_sources/{displayName}/daily/{yyyy-MM-dd}
     * by adding the detected carbon amount to "total_carbon_footprint".
     */
    private void updateFoodFirestore(float detectedFoodCarbon) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No user logged in; cannot update Firestore for food.");
            return;
        }
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            Log.e(TAG, "User has no display name; cannot update Firestore with display name as doc ID.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        DocumentReference docRef = db.collection("food_sources")
                .document(displayName)
                .collection("daily")
                .document(today);

        docRef.get().addOnSuccessListener(documentSnapshot -> {
            double currentCarbon = 0.0;
            if (documentSnapshot.exists()) {
                String carbonStr = documentSnapshot.getString("total_carbon_footprint");
                if (carbonStr != null && !carbonStr.isEmpty()) {
                    try {
                        currentCarbon = Double.parseDouble(carbonStr);
                    } catch (NumberFormatException e) {
                        currentCarbon = 0.0;
                    }
                }
            }
            double updatedCarbon = currentCarbon + detectedFoodCarbon;

            Map<String, Object> data = new HashMap<>();
            data.put("total_carbon_footprint", String.valueOf(updatedCarbon));

            docRef.set(data)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "Firestore daily doc updated with new food carbon: " + updatedCarbon))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Failed to update daily doc for food", e));
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get daily doc for displayName: " + displayName, e);
        });
    }
}

