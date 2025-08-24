// This is the complete and final code for MainActivity.java
// It correctly sets up the tabs and handles the UI padding.
package com.example.smartc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.smartc.databinding.ActivityMainBinding;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "com.example.smartc.EXTRA_ID";
    private ActivityMainBinding binding;
    private ViewPagerAdapter viewPagerAdapter;
    private boolean isAllFabsVisible;
    private ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private Uri tempImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = getResources().getDisplayMetrics().density;
            int paddingReduction = (int) (16 * density);
            binding.appBarLayout.setPadding(systemBars.left, systemBars.top - paddingReduction, systemBars.right, 0);
            binding.viewPager.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        setupTabs();
        setupResultLaunchers();
        setupFab();
    }

    private void setupTabs() {
        viewPagerAdapter = new ViewPagerAdapter(this);
        binding.viewPager.setAdapter(viewPagerAdapter);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("Reminders"); break;
                        case 1: tab.setText("Notes"); break;
                        case 2: tab.setText("Expenses"); break;
                    }
                }
        ).attach();
    }

    private void setupFab() {
        binding.fabCamera.setVisibility(View.GONE);
        binding.fabUploadImage.setVisibility(View.GONE);
        binding.fabTextNote.setVisibility(View.GONE);
        isAllFabsVisible = false;

        binding.fabAdd.setOnClickListener(v -> {
            if (!isAllFabsVisible) {
                binding.fabCamera.show();
                binding.fabUploadImage.show();
                binding.fabTextNote.show();
                isAllFabsVisible = true;
            } else {
                binding.fabCamera.hide();
                binding.fabUploadImage.hide();
                binding.fabTextNote.hide();
                isAllFabsVisible = false;
            }
        });

        binding.fabUploadImage.setOnClickListener(v -> openGallery());
        binding.fabCamera.setOnClickListener(v -> openCamera());
        binding.fabTextNote.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            startActivity(intent);
        });
    }

    private void setupResultLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) startShareActivityWithImage(uri);
        });
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success) startShareActivityWithImage(tempImageUri);
        });
        requestCameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openCamera();
            else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        });
    }

    private void openGallery() {
        galleryLauncher.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                File imageFile = File.createTempFile("temp_photo", ".jpg", getCacheDir());
                tempImageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", imageFile);
                cameraLauncher.launch(tempImageUri);
            } catch (IOException e) {
                Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show();
            }
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startShareActivityWithImage(Uri imageUri) {
        Intent intent = new Intent(this, ShareActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }
}