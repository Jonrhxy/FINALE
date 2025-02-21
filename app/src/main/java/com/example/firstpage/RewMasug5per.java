package com.example.firstpage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.Properties;
import java.util.Random;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class RewMasug5per extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userEmail;
    private static final String SENDER_EMAIL = "pagetest296@gmail.com";  // Change to your email
    private static final String SENDER_PASSWORD = "fflnedycbucwenen";  // Use App Password

    private Button claimBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rew_masug5per);

        // Initialize UI Elements
        claimBtn = findViewById(R.id.btn_masug5per);
        db = FirebaseFirestore.getInstance();

        // Get logged-in user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userEmail = user.getEmail();
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set Click Listener for the Claim Button
        claimBtn.setOnClickListener(v -> claimReward());
    }

    private void claimReward() {
        String rewardCode = generateRewardCode();

        // Store the reward code in Firestore
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DocumentReference userRef = db.collection("qr_claims").document(user.getEmail());

            userRef.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists() && documentSnapshot.contains(rewardCode)) {
                    Toast.makeText(this, "This reward has already been claimed.", Toast.LENGTH_SHORT).show();
                } else {
                    // Store the reward claim in Firestore
                    userRef.update(rewardCode, true)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Reward Claimed Successfully!", Toast.LENGTH_SHORT).show();
                                disableClaimButton(); // Disable button
                                sendRewardEmail(rewardCode); // Send email
                                navigateToFragmentRewardM(); // Redirect to FragmentRewardM
                            })
                            .addOnFailureListener(e -> userRef.set(Collections.singletonMap(rewardCode, true))
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "First Scan Recorded!", Toast.LENGTH_SHORT).show();
                                        disableClaimButton(); // Disable button
                                        sendRewardEmail(rewardCode); // Send email
                                        navigateToFragmentRewardM(); // Redirect to FragmentRewardM
                                    })
                                    .addOnFailureListener(e2 -> Toast.makeText(this, "Error Saving Scan", Toast.LENGTH_SHORT).show()));
                }
            }).addOnFailureListener(e -> Toast.makeText(this, "Error checking claim status.", Toast.LENGTH_SHORT).show());
        }
    }

    private void sendRewardEmail(String rewardCode) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");

                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                    }
                });

                // Email to the user
                Message userMessage = new MimeMessage(session);
                userMessage.setFrom(new InternetAddress(SENDER_EMAIL));
                userMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail));
                userMessage.setSubject("🎉 Reward Claimed Successfully!");
                userMessage.setText("Congratulations!\n\nYou have successfully claimed your reward.\nYour unique code: " + rewardCode + "\n\nEnjoy!");

                // Email to the sender (admin)
                Message senderMessage = new MimeMessage(session);
                senderMessage.setFrom(new InternetAddress(SENDER_EMAIL));
                senderMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(SENDER_EMAIL));
                senderMessage.setSubject("New Reward Claimed!");
                senderMessage.setText("A user has claimed a reward!\n\nUser: " + userEmail + "\nReward Code: " + rewardCode);

                // Send both emails
                Transport.send(userMessage);
                Transport.send(senderMessage);

                runOnUiThread(() -> Toast.makeText(RewMasug5per.this, "Check Your Email!", Toast.LENGTH_SHORT).show());

            } catch (MessagingException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(RewMasug5per.this, "Error sending email.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void disableClaimButton() {
        runOnUiThread(() -> {
            claimBtn.setEnabled(false);
            claimBtn.setText("Reward Claimed");
        });
    }

    private void navigateToFragmentRewardM() {
        Intent intent = new Intent(RewMasug5per.this, FragmentRewardM.class);
        startActivity(intent);
        finish();
    }

    private String generateRewardCode() {
        Random random = new Random();
        int randomNum = 10000 + random.nextInt(90000); // Generate a 5-digit random number
        return "MASUG5PER-" + randomNum;
    }
}