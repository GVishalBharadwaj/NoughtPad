package com.vishal.noughtpad;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

public class PrivacyPolicyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This must be called before setContentView
        EdgeToEdge.enable(this);

        // Check if the policy has already been accepted BEFORE showing the layout
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("policy_accepted", false)) {
            Intent intent = new Intent(PrivacyPolicyActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return; // Exit before showing the policy screen
        }

        setContentView(R.layout.activity_privacy_policy);

        // This listener adds the correct padding to avoid the system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button acceptButton = findViewById(R.id.button_accept);
        acceptButton.setOnClickListener(v -> {
            // Save that the user has accepted the policy
            prefs.edit().putBoolean("policy_accepted", true).apply();

            // Go to the main app screen
            Intent intent = new Intent(PrivacyPolicyActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Prevent user from coming back to this screen
        });
    }
}