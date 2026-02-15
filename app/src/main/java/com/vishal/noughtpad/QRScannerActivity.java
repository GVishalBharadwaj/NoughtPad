package com.vishal.noughtpad;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.vishal.noughtpad.databinding.ActivityQrScannerBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Size;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.Camera;
import androidx.camera.core.ImageProxy;

public class QRScannerActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CAMERA = 100;
    private static final int PAYMENT_REQUEST_CODE = 200;
    private ActivityQrScannerBinding binding;
    private ExecutorService cameraExecutor;
    private boolean isScanning = true;
    private Camera camera;
    private boolean isFlashOn = false;
    private ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;
    private BarcodeScanner scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize ML Kit Scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA },
                    PERMISSION_REQUEST_CAMERA);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        binding.buttonFlash.setOnClickListener(v -> toggleFlash());

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                scanImageFromUri(uri);
            }
        });

        binding.buttonGallery.setOnClickListener(v -> {
            galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);
            binding.buttonFlash.setText(isFlashOn ? "Flash Off" : "Flash On");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                // ML Kit works well with default resolutions, but 1280x720 is a good balance
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (!isScanning) {
                        imageProxy.close();
                        return;
                    }
                    processImageProxy(imageProxy);
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                // Add Tap-to-Focus
                binding.viewFinder.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP && camera != null) {
                        androidx.camera.core.MeteringPointFactory factory = binding.viewFinder
                                .getMeteringPointFactory();
                        androidx.camera.core.MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                        androidx.camera.core.FocusMeteringAction action = new androidx.camera.core.FocusMeteringAction.Builder(
                                point).build();
                        camera.getCameraControl().startFocusAndMetering(action);
                        v.performClick();
                    }
                    return true;
                });

            } catch (ExecutionException | InterruptedException e) {
                Log.e("QRScanner", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!isScanning)
                        return;

                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null && rawValue.startsWith("upi://")) {
                            isScanning = false;
                            Log.d("QRScanner", "Scanned UPI: " + rawValue);
                            handleUpiUri(rawValue);
                            break; // Stop after first valid UPI QR
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Log failure but keep scanning
                    // Log.e("QRScanner", "Scan failed", e);
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void scanImageFromUri(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        boolean found = false;
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null && rawValue.startsWith("upi://")) {
                                found = true;
                                handleUpiUri(rawValue);
                                break;
                            }
                        }
                        if (!found) {
                            Toast.makeText(this, "No UPI QR code found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("QRGallery", "ML Kit failed", e);
                        Toast.makeText(this, "Failed to scan image", Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            Log.e("QRGallery", "Error loading image", e);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private String lastPayeeName;
    private String lastPayeeVpa;
    private String lastAmount;
    private String lastMcc; // Merchant Category Code

    private void handleUpiUri(String upiUriString) {
        Uri upiUri = Uri.parse(upiUriString);
        lastPayeeName = upiUri.getQueryParameter("pn");
        lastPayeeVpa = upiUri.getQueryParameter("pa");
        lastAmount = upiUri.getQueryParameter("am");
        lastMcc = upiUri.getQueryParameter("mc");

        // Always show the dialog to give the user a choice (Pay vs Record)
        showTransactionDialog(lastPayeeName, lastPayeeVpa, upiUriString);
    }

    private void showTransactionDialog(String name, String vpa, String originalUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Transaction Details");

        String payeeDisplay = (name != null && !name.isEmpty()) ? name : vpa;
        builder.setMessage("Payee: " + payeeDisplay);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("0.00");

        // Pre-fill amount if present
        if (lastAmount != null && !lastAmount.isEmpty()) {
            input.setText(lastAmount);
        }

        // Add padding to EditText
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        builder.setView(input);

        builder.setPositiveButton("Pay via UPI", (dialog, which) -> {
            String amountStr = input.getText().toString();
            if (!amountStr.isEmpty()) {
                lastAmount = amountStr;

                // Use Uri.Builder for robust parameter appending
                Uri.Builder uriBuilder = Uri.parse(originalUri).buildUpon();
                // Clear existing amount param to avoid duplicates if we are overriding
                uriBuilder.clearQuery();
                // Re-add all original params except 'am'
                Uri original = Uri.parse(originalUri);
                for (String key : original.getQueryParameterNames()) {
                    if (!"am".equals(key)) {
                        uriBuilder.appendQueryParameter(key, original.getQueryParameter(key));
                    }
                }

                uriBuilder.appendQueryParameter("am", amountStr);

                if (original.getQueryParameter("cu") == null) {
                    uriBuilder.appendQueryParameter("cu", "INR");
                }
                uriBuilder.appendQueryParameter("refUrl", "https://github.com/GVishalBharadwaj/NoughtPad");

                launchUpiPayment(uriBuilder.build());
            } else {
                Toast.makeText(this, "Amount is required to pay", Toast.LENGTH_SHORT).show();
                isScanning = true;
            }
        });

        builder.setNeutralButton("Save Record Only", (dialog, which) -> {
            String amountStr = input.getText().toString();
            lastAmount = !amountStr.isEmpty() ? amountStr : "0.00";
            proceedToSaveRecord();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            isScanning = true;
            dialog.cancel();
        });

        // Ensure dialog doesn't block scanning if cancelled by outside touch
        builder.setOnCancelListener(dialog -> isScanning = true);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Focus if amount is missing
        if (lastAmount == null || lastAmount.isEmpty()) {
            input.requestFocus();
        }
    }

    private void proceedToSaveRecord() {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("EXTRA_CATEGORY", "RECEIPT");

        // Pass captured details
        if (lastPayeeName != null)
            intent.putExtra("EXTRA_TITLE", lastPayeeName);
        if (lastPayeeVpa != null)
            intent.putExtra("EXTRA_DESCRIPTION", "Paid to: " + lastPayeeVpa);
        if (lastAmount != null)
            intent.putExtra("EXTRA_AMOUNT", lastAmount);
        if (lastMcc != null)
            intent.putExtra("EXTRA_MCC", lastMcc);

        startActivity(intent);
        finish();
    }

    private void launchUpiPayment(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        Intent chooser = Intent.createChooser(intent, "Pay with");

        try {
            startActivityForResult(chooser, PAYMENT_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No UPI app found", Toast.LENGTH_SHORT).show();
            isScanning = true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PAYMENT_REQUEST_CODE) {
            showPaymentConfirmationDialog();
        }
    }

    private void showPaymentConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Payment Status")
                .setMessage("Was the payment successful?")
                .setPositiveButton("Yes, Record It", (dialog, which) -> {
                    Intent intent = new Intent(this, DetailActivity.class);
                    intent.putExtra("EXTRA_CATEGORY", "RECEIPT");

                    // Pass captured details
                    if (lastPayeeName != null)
                        intent.putExtra("EXTRA_TITLE", lastPayeeName);
                    if (lastPayeeVpa != null)
                        intent.putExtra("EXTRA_DESCRIPTION", "Paid to: " + lastPayeeVpa);
                    if (lastAmount != null)
                        intent.putExtra("EXTRA_AMOUNT", lastAmount);
                    if (lastMcc != null)
                        intent.putExtra("EXTRA_MCC", lastMcc);

                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No / Cancel", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (scanner != null) {
            scanner.close();
        }
    }
}
