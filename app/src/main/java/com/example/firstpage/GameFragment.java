package com.example.firstpage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class GameFragment extends Fragment {

    private TextView tvDate, tvMonthYear, tvStreakCount, tvAttendanceStatus, tvTotalPoints;
    private ImageView ivCheckmark;
    private Button btnMarkAttendance;
    private int streakCount = 0;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public GameFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI Elements
        tvDate = view.findViewById(R.id.tvDate);
        tvMonthYear = view.findViewById(R.id.tvMonthYear);
        tvStreakCount = view.findViewById(R.id.tvStreakCount);
        tvAttendanceStatus = view.findViewById(R.id.tvAttendanceStatus);
        ivCheckmark = view.findViewById(R.id.ivCheckmark);
        tvTotalPoints = view.findViewById(R.id.TotalP);

        btnMarkAttendance = view.findViewById(R.id.btnMarkAttendance);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize SharedPreferences
        sharedPreferences = getActivity().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE);

        // Retrieve saved streak count and last marked date
        streakCount = sharedPreferences.getInt("streakCount", 0);
        String lastMarkedDate = sharedPreferences.getString("lastMarkedDate", "");

        // Update UI with saved data
        tvStreakCount.setText(String.format(Locale.getDefault(), "%d - Day Streak", streakCount));

        // Update Date
        updateDate();

        // Set Click Listeners for Buttons
        view.findViewById(R.id.gameB).setOnClickListener(v -> openActivity(Game.class));
        view.findViewById(R.id.reward).setOnClickListener(v -> openActivity(FragmentRewardM.class));
        view.findViewById(R.id.feature2B).setOnClickListener(v -> openActivity(LeaderBoards.class));
        view.findViewById(R.id.quizB).setOnClickListener(v -> replaceFragment(new QuizFrag()));
        view.findViewById(R.id.feature1B).setOnClickListener(v -> replaceFragment(new VideoFrag()));

        // Mark attendance button
        btnMarkAttendance.setOnClickListener(this::markAttendance);

        // Check if it's a new day and reset attendance button if necessary
        checkAndResetAttendance(lastMarkedDate);

        // Fetch the points for the current user
        fetchAndUpdatePoints();
    }

    private void markAttendance(View view) {
        ivCheckmark.setVisibility(View.VISIBLE); // Make the checkmark visible
        tvAttendanceStatus.setText("Attendance Marked");
        streakCount++;

        // Update the streak count in UI
        tvStreakCount.setText(String.format(Locale.getDefault(), "%d - Day Streak", streakCount));

        // Save updated streak count and today's date in SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("streakCount", streakCount);
        editor.putString("lastMarkedDate", getCurrentDate());
        editor.putBoolean("attendanceMarked", true); // Flag for attendance status
        editor.apply();

        // Disable the button completely
        btnMarkAttendance.setEnabled(false);
        btnMarkAttendance.setAlpha(0.5f);
    }

    private void checkAndResetAttendance(String lastMarkedDate) {
        String todayDate = getCurrentDate();
        boolean attendanceMarked = sharedPreferences.getBoolean("attendanceMarked", false);

        if (todayDate.equals(lastMarkedDate) && attendanceMarked) {
            // Keep the button disabled if attendance has already been marked today
            btnMarkAttendance.setEnabled(false);
            btnMarkAttendance.setAlpha(0.5f);
            ivCheckmark.setVisibility(View.VISIBLE);
            tvAttendanceStatus.setText("Attendance Marked");
        } else {
            // Enable the button for a new day
            btnMarkAttendance.setEnabled(true);
            btnMarkAttendance.setAlpha(1.0f);
            ivCheckmark.setVisibility(View.GONE);
            tvAttendanceStatus.setText("");

            // Reset attendance status flag for the new day
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("attendanceMarked", false);
            editor.apply();
        }
    }

    private void fetchAndUpdatePoints() {
        String username = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getDisplayName() : null;

        if (username == null || username.isEmpty()) {
            Toast.makeText(getActivity(), "Error: Username not found!", Toast.LENGTH_LONG).show();
            return;
        }

        DocumentReference gameRef = db.collection("Games").document(username);
        DocumentReference quizRef = db.collection("Quiz").document(username);
        DocumentReference quizRef2 = db.collection("Quiz2").document(username);

        gameRef.get().addOnSuccessListener(gameSnapshot -> {
            int gamePoints = gameSnapshot.exists() && gameSnapshot.contains("highScore") ?
                    gameSnapshot.getLong("highScore").intValue() : 0;

            quizRef.get().addOnSuccessListener(quizSnapshot -> {
                int quizPoints = quizSnapshot.exists() && quizSnapshot.contains("score") ?
                        quizSnapshot.getLong("score").intValue() : 0;

                quizRef2.get().addOnSuccessListener(quiz2Snapshot -> {
                    int quiz2Points = quiz2Snapshot.exists() && quiz2Snapshot.contains("score") ?
                            quiz2Snapshot.getLong("score").intValue() : 0;

                    // Compute total points
                    int totalPoints = gamePoints + quizPoints + quiz2Points;
                    tvTotalPoints.setText(String.format(Locale.getDefault(), "Total Points: %d", totalPoints));
                }).addOnFailureListener(e -> {
                    Log.e("GameFragment", "Error fetching quiz points", e);
                    Toast.makeText(getActivity(), "Error fetching quiz points", Toast.LENGTH_SHORT).show();
                });

            }).addOnFailureListener(e -> {
                Log.e("GameFragment", "Error fetching game points", e);
                Toast.makeText(getActivity(), "Error fetching game points", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private String getCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return dateFormat.format(Calendar.getInstance().getTime());
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getParentFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void updateDate() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());

        String todayDate = new SimpleDateFormat("dd", Locale.getDefault()).format(calendar.getTime());
        String monthYear = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.getTime());

        tvDate.setText(todayDate);
        tvMonthYear.setText(monthYear);
    }

    private void openActivity(Class<?> activityClass) {
        Intent intent = new Intent(getActivity(), activityClass);
        startActivity(intent);
    }
}