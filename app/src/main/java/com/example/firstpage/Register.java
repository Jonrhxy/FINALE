package com.example.firstpage;

import static android.content.ContentValues.TAG;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

public class Register extends AppCompatActivity {

    private EditText txtUsername, txtFname, txtLname, txtEmail, txtpass, txtconpass, studentId;
    private Button SignUpbtn;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView textViewLoginNow;
    private CheckBox checkboxTerms;
    private final boolean isRegistering = false;

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth == null) {
            mAuth = FirebaseAuth.getInstance();
        }
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified() && !isRegistering) {
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        txtUsername = findViewById(R.id.txtUname);
        txtFname = findViewById(R.id.txtFname);
        txtLname = findViewById(R.id.txtLname);
        txtEmail = findViewById(R.id.txtEmail);
        txtpass = findViewById(R.id.txtpass);
        txtconpass = findViewById(R.id.txtconpass);
        studentId = findViewById(R.id.student_id);
        SignUpbtn = findViewById(R.id.SignUpbtn);
        progressBar = findViewById(R.id.progressBar);
        textViewLoginNow = findViewById(R.id.loginNow);
        checkboxTerms = findViewById(R.id.checkboxTerms);

        // Show terms and conditions dialog on checkbox click
        checkboxTerms.setOnClickListener(v -> showTermsDialog());

        SignUpbtn.setOnClickListener(v -> {
            if (!checkboxTerms.isChecked()) {
                Toast.makeText(getApplicationContext(), "You must agree to the terms and conditions.", Toast.LENGTH_SHORT).show();
                return;
            }
            signUpUser();
        });

        textViewLoginNow.setOnClickListener(view -> {
            startActivity(new Intent(getApplicationContext(), Signin.class));
            finish();
        });

        VideoView videoView = findViewById(R.id.videoViewBackground);
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.mbgblur);
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoView.start();
        });
    }

    // Helper method to validate names (only letters)
    private boolean isValidName(String name) {
        return name.matches("^[a-zA-Z ]+$");
    }

    // Helper method to validate student ID (numbers and hyphens)
    private boolean isValidStudentId(String id) {
        return id.matches("^[0-9]+-[0-9]+$"); // Ensures proper format like "21-1977"
    }

    private void showTermsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Terms and Conditions");

        String terms = "Terms and Conditions for C Verde\n" +
                "\n" +
                "1. Acceptance of Terms\n" +
                "•\tBy downloading, installing, or using the C-Verde application, you agree to be bound by these Terms and Conditions. If you do not agree to these terms, please do not use the application.\n" +
                "\n" +
                "2. Use of the Application\n" +
                "•\tC-Verde is a carbon footprint tracker that allows users to monitor and reduce their environmental impact. By using the application, you agree to:\n" +
                "•\tProvide accurate data regarding your activities to ensure accurate carbon footprint tracking.\n" +
                "•\tUse the app for lawful purposes and in compliance with all applicable laws.\n" +
                "•\tAvoid any unauthorized access, tampering, or reverse engineering of the application.\n" +
                "\n" +
                "3. Data Collection and Privacy\n" +
                "•\tC-Verde collects and processes personal information, such as your daily activities, energy usage, and travel habits, to calculate your carbon footprint. The information is stored securely and used solely for the purpose of improving your experience with the application.\n" +
                "•\tYour data will not be shared with third parties without your consent, except as required by law.\n" +
                "•\tWe take reasonable steps to ensure your data is protected, but no system is completely secure. By using the app, you acknowledge the risks of data breaches.\n" +
                "\n" +
                "4. User Responsibilities\n" +
                "You are responsible for:\n" +
                "•\tEnsuring that the information you provide is accurate and up-to-date.\n" +
                "•\tRegularly updating the app to benefit from new features and improvements.\n" +
                "•\tComplying with all local laws regarding environmental reporting if applicable.\n" +
                "\n" +
                "5. Accuracy of Information\n" +
                "•\tWhile C-Verde strives to provide accurate carbon footprint calculations, the results depend on the data you input. Variations in activities, energy sources, and other factors can lead to estimates rather than exact values. Therefore, C-Verde cannot guarantee the absolute accuracy of the carbon footprint estimates provided.\n" +
                "\n" +
                "6. Limitation of Liability\n" +
                "•\tC-Verde and its developers will not be liable for any direct, indirect, incidental, or consequential damages arising from your use of the application, including errors in carbon footprint estimations, data loss, or interruptions to the service.\n" +
                "\n" +
                "7. Updates and Changes\n" +
                "•\tC-Verde reserves the right to modify or update these Terms and Conditions at any time. Users will be notified of any changes via the app or email. Continued use of the app after changes are posted signifies your acceptance of the new terms.\n" +
                "\n" +
                "8. Termination\n" +
                "•\tWe reserve the right to terminate your access to C Verde if you violate any of these Terms and Conditions. Termination can occur without prior notice.\n" +
                "\n" +
                "9. Governing Law\n" +
                "•\tThese Terms and Conditions are governed by and construed in accordance with the laws.\n" +
                "\n" +
                "10. Contact Information\n" +
                "•\tFor any questions or concerns regarding these Terms and Conditions or the use of the C-Verde application, you can contact us at C-Verde Facebook page.\n";

        SpannableString spannableTerms = new SpannableString(terms);

        String[] titles = { "Terms and Conditions for C Verde",
                "1. Acceptance of Terms",
                "2. Use of the Application",
                "3. Data Collection and Privacy",
                "4. User Responsibilities",
                "5. Accuracy of Information",
                "6. Limitation of Liability",
                "7. Updates and Changes",
                "8. Termination",
                "9. Governing Law",
                "10. Contact Information",};
        for (String title : titles) {
            int startIndex = terms.indexOf(title);
            if (startIndex != -1) {
                int endIndex = startIndex + title.length();
                spannableTerms.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, endIndex, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        builder.setMessage(spannableTerms);
        builder.setPositiveButton("I Agree", (dialog, which) -> checkboxTerms.setChecked(true));
        builder.setNegativeButton("Cancel", (dialog, which) -> checkboxTerms.setChecked(false));
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void signUpUser() {
        String username = txtUsername.getText().toString().trim();
        String firstName = txtFname.getText().toString().trim();
        String lastName = txtLname.getText().toString().trim();
        String email = txtEmail.getText().toString().trim();
        String password = txtpass.getText().toString().trim();
        String confirmPassword = txtconpass.getText().toString().trim();
        String studentID = studentId.getText().toString().trim();

        if (TextUtils.isEmpty(username) || username.length() < 3) {
            txtUsername.setError("Username must be at least 3 characters long.");
            txtUsername.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(firstName) || !isValidName(firstName)) {
            txtFname.setError("First name must only contain letters.");
            txtFname.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(lastName) || !isValidName(lastName)) {
            txtLname.setError("Last name must only contain letters.");
            txtLname.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(studentID) || !isValidStudentId(studentID)) {
            studentId.setError("Student ID must be in the format 'XX-XXXX' (numbers and a hyphen).");
            studentId.requestFocus();
            return;
        }

        // List of allowed student IDs
        List<String> allowedIDs = Arrays.asList(
                "21-1977", "21-1959", "21-1964",
                "21-1950", "21-1947", "21-1975",
                "21-1935", "21-1944", "21-1954",
                "21-1949", "21-1798", "21-1874",
                "21-1990", "21-1936", "21-1980",
                "21-1963"
        );
        if (!allowedIDs.contains(studentID)) {
            studentId.setError("Invalid student ID.");
            studentId.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            txtEmail.setError("Email is required.");
            txtEmail.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtEmail.setError("Please provide a valid email.");
            txtEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            txtpass.setError("Password is required.");
            txtpass.requestFocus();
            return;
        }
        if (password.length() < 8) {
            txtpass.setError("Password must be at least 8 characters.");
            txtpass.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            txtconpass.setError("Passwords do not match.");
            txtconpass.requestFocus();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);

        // Check if the student ID is already registered
        db.collection("users")
                .whereEqualTo("studentID", studentID)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            progressBar.setVisibility(View.GONE);
                            studentId.setError("Student ID is already registered.");
                            studentId.requestFocus();
                            return;
                        } else {
                            // Proceed with registration since the student ID is unique
                            mAuth.createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this, authTask -> {
                                        progressBar.setVisibility(View.GONE);
                                        if (authTask.isSuccessful()) {
                                            FirebaseUser user = mAuth.getCurrentUser();
                                            if (user != null) {
                                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                                        .setDisplayName(username)
                                                        .build();
                                                user.updateProfile(profileUpdates)
                                                        .addOnCompleteListener(task1 -> {
                                                            if (task1.isSuccessful()) {
                                                                saveUserToFirestore(user, username, firstName, lastName, email, studentID);
                                                                sendVerificationEmail(user);
                                                            }
                                                        });
                                            }
                                        } else {
                                            if (authTask.getException() instanceof FirebaseAuthUserCollisionException) {
                                                txtEmail.setError("Email is already registered.");
                                            } else {
                                                Toast.makeText(Register.this, "Registration failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(Register.this, "Error checking student ID uniqueness.", Toast.LENGTH_SHORT).show();
                    }
                });
    }



    private boolean validateInput(String username, String firstName, String lastName, String email, String password, String confirmPassword, String studentID) {
        if (TextUtils.isEmpty(username) || username.length() < 3) {
            txtUsername.setError("Username must be at least 3 characters long.");
            txtUsername.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(firstName) || !isValidName(firstName)) {
            txtFname.setError("First name must only contain letters and spaces.");
            txtFname.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(lastName) || !isValidName(lastName)) {
            txtLname.setError("Last name must only contain letters and spaces.");
            txtLname.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(studentID) || !isValidStudentId(studentID)) {
            studentId.setError("Student ID must be in the format 'XX-XXXX'.");
            studentId.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtEmail.setError("Please provide a valid email.");
            txtEmail.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 8 ) {
            txtpass.setError("Password must be at least 8 characters.");
            txtpass.requestFocus();
            return false;
        }
        if (!password.equals(confirmPassword)) {
            txtconpass.setError("Passwords do not match.");
            txtconpass.requestFocus();
            return false;
        }
        return true;
    }

    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(Register.this, "Verification email sent. Please check your email.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Register.this, Signin.class));
                        finish();
                    } else {
                        Toast.makeText(Register.this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String username, String firstName, String lastName, String email, String studentID) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("email", email);
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("studentID", studentID);
        userData.put("registeredAt", FieldValue.serverTimestamp());

        db.collection("users").document(username) // Use username as document ID
                .set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User profile saved to Firestore with username as ID"))
                .addOnFailureListener(e -> Log.w(TAG, "Error saving user profile", e));
    }
}
