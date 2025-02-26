package com.example.firstpage;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class VehicleStatusActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_status);

        // Retrieve the prompt message from the intent.
        String message = getIntent().getStringExtra("message");
        TextView promptTextView = findViewById(R.id.textViewPrompt);
        if (message != null && !message.isEmpty()) {
            promptTextView.setText(message);
        } else {
            promptTextView.setText("Are you still in a vehicle?");
        }

        Button buttonYes = findViewById(R.id.buttonYes);
        Button buttonNo = findViewById(R.id.buttonNo);

        buttonYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent responseIntent = new Intent("VEHICLE_STATUS_RESPONSE");
                responseIntent.putExtra("in_vehicle", true);
                sendBroadcast(responseIntent);
                finish();
            }
        });

        buttonNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent responseIntent = new Intent("VEHICLE_STATUS_RESPONSE");
                responseIntent.putExtra("in_vehicle", false);
                sendBroadcast(responseIntent);
                finish();
            }
        });
    }
}
