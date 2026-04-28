package com.example.firealarmapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firealarmapp.api.ApiService;
import com.example.firealarmapp.databinding.ActivityAddDeviceBinding;
import com.example.firealarmapp.models.ClaimDeviceRequest;
import com.example.firealarmapp.utils.RetrofitClient;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddDeviceActivity extends AppCompatActivity {

    private ActivityAddDeviceBinding binding;
    private ApiService apiService;

    private static final String[] LOCATIONS = {
            "Living Room", "Bedroom", "Kitchen",
            "Bathroom", "Garage", "Office",
            "Hallway", "Basement", "Attic", "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddDeviceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = RetrofitClient.getApiService(this);

        // Setup location spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, LOCATIONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerLocation.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnClaim.setOnClickListener(v -> claimDevice());
    }

    private void claimDevice() {
        String deviceId = binding.etDeviceId.getText().toString().trim().toUpperCase();
        String token    = binding.etClaimToken.getText().toString().trim().toUpperCase();
        String location = LOCATIONS[binding.spinnerLocation.getSelectedItemPosition()];

        if (deviceId.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Please fill in Device ID and Token", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnClaim.setEnabled(false);
        binding.btnClaim.setText("Adding...");
        binding.tvStatus.setVisibility(View.GONE);

        ClaimDeviceRequest request = new ClaimDeviceRequest(deviceId, token, location);
        apiService.claimDevice(request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                binding.btnClaim.setEnabled(true);
                binding.btnClaim.setText("Add Device");

                if (response.isSuccessful() && response.body() != null) {
                    // Success
                    binding.tvStatus.setText("✅ Device added successfully!");
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_light));
                    binding.tvStatus.setVisibility(View.VISIBLE);

                    Toast.makeText(AddDeviceActivity.this,
                            "Device " + deviceId + " added!", Toast.LENGTH_LONG).show();

                    // Return to dashboard after 1.5 seconds
                    binding.getRoot().postDelayed(() -> {
                        setResult(RESULT_OK, new Intent().putExtra("deviceId", deviceId));
                        finish();
                    }, 1500);

                } else {
                    // Error from server
                    String errMsg = "Failed to add device";
                    try {
                        if (response.errorBody() != null) {
                            errMsg = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}

                    if (response.code() == 400) {
                        if (errMsg.contains("already claimed")) {
                            errMsg = "❌ This device is already claimed by another user.";
                        } else if (errMsg.contains("Invalid claim token")) {
                            errMsg = "❌ Invalid claim token. Please check the token again.";
                        } else if (errMsg.contains("not found")) {
                            errMsg = "❌ Device ID not found. Please check again.";
                        }
                    }

                    binding.tvStatus.setText(errMsg);
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light));
                    binding.tvStatus.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                binding.btnClaim.setEnabled(true);
                binding.btnClaim.setText("Add Device");
                binding.tvStatus.setText("❌ Network error: " + t.getMessage());
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light));
                binding.tvStatus.setVisibility(View.VISIBLE);
            }
        });
    }
}
