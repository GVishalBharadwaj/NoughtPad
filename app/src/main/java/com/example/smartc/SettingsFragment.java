package com.example.smartc;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;

// ✅ It must extend PreferenceFragmentCompat
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // This method is now correctly recognized
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
    }
}