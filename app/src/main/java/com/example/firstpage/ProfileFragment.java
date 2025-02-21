package com.example.firstpage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private static final String PREFS_NAME = "loginPrefs";
    private static final String PREF_IS_LOGGED_IN = "isLoggedIn";

    private TextView titleName;
    private LinearLayout logoutButton, editButton;
    private ImageView profileImg;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String username;

    public ProfileFragment() {
        // Default constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_profile_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() == null) return; // Prevent crashes if activity is null

        initializeViews(view);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        fetchUserData();
        setupListeners();
    }

    private void initializeViews(View view) {
        titleName = view.findViewById(R.id.titleName);
        editButton = view.findViewById(R.id.editProfile);
        logoutButton = view.findViewById(R.id.btnlogout);
        profileImg = view.findViewById(R.id.profileImg);
    }

    private void setupListeners() {
        logoutButton.setOnClickListener(v -> handleLogout());
        editButton.setOnClickListener(v -> startActivity(new Intent(getActivity(), EditProfile.class)));
    }

    private void fetchUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userEmail = currentUser.getEmail();
        if (userEmail == null) return;

        db.collection("users")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        username = document.getString("username");
                        titleName.setText(username);
                    }
                })
                .addOnFailureListener(e -> Log.e("ProfileFragment", "Failed to get username: " + e.getMessage()));
    }

    private void handleLogout() {
        FirebaseAuth.getInstance().signOut();

        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_IS_LOGGED_IN, false);
        editor.apply();

        Intent intent = new Intent(requireActivity(), Signin.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        requireActivity().finish();
    }
}
