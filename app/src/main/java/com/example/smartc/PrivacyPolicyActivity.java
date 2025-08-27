package com.example.smartc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("policy_accepted", false)) {
            Intent intent = new Intent(PrivacyPolicyActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return; // Don't show the policy screen
        }

        setContentView(R.layout.activity_privacy_policy);

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