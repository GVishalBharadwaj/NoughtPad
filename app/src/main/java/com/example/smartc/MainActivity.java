package com.example.smartc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.smartc.databinding.ActivityMainBinding;
import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements ReminderAdapter.OnItemClickListener {

    // A key for passing the item ID to DetailActivity
    public static final String EXTRA_ID = "com.example.smartc.EXTRA_ID";

    // UI and Data Components
    private ActivityMainBinding binding;
    private ReminderViewModel reminderViewModel;
    private ReminderAdapter adapter;

    // State for the Floating Action Buttons
    private boolean isAllFabsVisible;

    // Modern way to handle results from other activities (gallery, camera, permissions)
    private ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private Uri tempImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set our custom toolbar as the app's action bar
        setSupportActionBar(binding.toolbar);

        // Set up all the components of our screen
        setupRecyclerView();
        setupViewModel();
        setupResultLaunchers();
        setupFab();
    }

    private void setupFab() {
        // Hide the smaller FABs initially
        binding.fabCamera.setVisibility(View.GONE);
        binding.fabUploadImage.setVisibility(View.GONE);
        isAllFabsVisible = false;

        // Main FAB click listener to show/hide the smaller buttons
        binding.fabAdd.setOnClickListener(v -> {
            if (!isAllFabsVisible) {
                binding.fabCamera.show();
                binding.fabUploadImage.show();
                isAllFabsVisible = true;
            } else {
                binding.fabCamera.hide();
                binding.fabUploadImage.hide();
                isAllFabsVisible = false;
            }
        });

        binding.fabUploadImage.setOnClickListener(v -> openGallery());
        binding.fabCamera.setOnClickListener(v -> openCamera());
    }

    private void setupResultLaunchers() {
        // Launcher for the modern Photo Picker (handles gallery access)
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        // An image was selected from the gallery.
                        // Now, send its URI to ShareActivity for processing.
                        startShareActivityWithImage(uri);
                    }
                });

        // Launcher for taking a picture with the camera
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success) {
                        // The photo was successfully saved to tempImageUri.
                        // Now, send its URI to ShareActivity for processing.
                        startShareActivityWithImage(tempImageUri);
                    }
                });

        // Launcher for requesting the camera permission (unchanged)
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ✅ ADD THIS HELPER METHOD
    private void startShareActivityWithImage(Uri imageUri) {
        Intent intent = new Intent(this, ShareActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        // Add flags to grant read permission to the receiving activity
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void openGallery() {
        // Launch the photo picker, which safely handles all photo permissions
        galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void openCamera() {
        // Check if we already have camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Create a temporary file to store the camera image
                File imageFile = File.createTempFile("temp_photo", ".jpg", getCacheDir());
                tempImageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", imageFile);
                cameraLauncher.launch(tempImageUri);
            } catch (IOException e) {
                Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show();
            }
        } else {
            // If we don't have permission, request it
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupRecyclerView() {
        binding.recyclerViewReminders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReminderAdapter();
        binding.recyclerViewReminders.setAdapter(adapter);
        adapter.setOnItemClickListener(this);
    }

    private void setupViewModel() {
        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);
        // Observe the data and automatically update the adapter's list
        reminderViewModel.getAllItems().observe(this, adapter::submitList);
    }


    @Override
    public void onItemClick(ReminderItem item) {
        Intent intent = new Intent(MainActivity.this, DetailActivity.class);
        // Pass the unique ID of the clicked item to the DetailActivity
        intent.putExtra(EXTRA_ID, item.id);
        startActivity(intent);
    }
}