package com.example.firstpage;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.firstpage.Chall1;
import com.example.firstpage.R;

public class InProgressFragment extends Fragment {

    private Button btnStartChallenge1;
    private Button btnStartChallenge2;
    private Button btnStartChallenge3;
    private Button btnStartChallenge4;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_in_progress, container, false);

        // Initialize the button
        btnStartChallenge1 = view.findViewById(R.id.btnStartChallenge1);

        // Set the click listener
        btnStartChallenge1.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), Chall1.class);
            startActivity(intent);
        });


        btnStartChallenge2 = view.findViewById(R.id.btnStartChallenge2);

        // Set the click listener
        btnStartChallenge2.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), Chall1.class);
            startActivity(intent);
        });

        btnStartChallenge3 = view.findViewById(R.id.btnStartChallenge3);

        // Set the click listener
        btnStartChallenge3.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), Bfast1Fragment.class);
            startActivity(intent);
        });

        btnStartChallenge4 = view.findViewById(R.id.btnStartChallenge4);

        // Set the click listener
        btnStartChallenge4.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), Transportation.class);
            startActivity(intent);
        });


        return view;
    }
}