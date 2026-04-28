package com.example.firealarmapp.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firealarmapp.api.ApiService;
import com.example.firealarmapp.databinding.ActivityAdminProvisionBinding;
import com.example.firealarmapp.utils.RetrofitClient;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProvisionActivity extends AppCompatActivity {

    private ActivityAdminProvisionBinding binding;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminProvisionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = RetrofitClient.getApiService(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnProvision.setOnClickListener(v -> provisionDevice());

        binding.btnCopyToken.setOnClickListener(v -> {
            String token = binding.tvGeneratedToken.getText().toString();
            if (!token.isEmpty() && !token.equals("XXX-XXX-XXX")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Claim Token", token);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void provisionDevice() {
        String deviceId = binding.etDeviceId.getText().toString().trim().toUpperCase();
        String name = binding.etDeviceName.getText().toString().trim();
        String mac = binding.etMacAddress.getText().toString().trim();

        if (deviceId.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập Device ID và Tên thiết bị", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mac.isEmpty()) mac = deviceId; // Fallback to deviceId if no mac provided

        binding.btnProvision.setEnabled(false);
        binding.btnProvision.setText("GENERATING...");
        binding.tvStatus.setVisibility(View.GONE);
        binding.tokenContainer.setVisibility(View.GONE);

        Map<String, String> request = new HashMap<>();
        request.put("deviceId", deviceId);
        request.put("name", name);
        request.put("macAddress", mac);

        apiService.provisionDevice(request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                binding.btnProvision.setEnabled(true);
                binding.btnProvision.setText("GENERATE TOKEN");

                if (response.isSuccessful() && response.body() != null) {
                    String token = (String) response.body().get("claimToken");
                    
                    binding.tokenContainer.setVisibility(View.VISIBLE);
                    binding.tvGeneratedToken.setText(token);
                    
                    binding.tvStatus.setText("Khởi tạo thành công!");
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_light));
                    binding.tvStatus.setVisibility(View.VISIBLE);
                    
                    // Clear inputs for next device
                    binding.etDeviceId.setText("");
                    binding.etDeviceName.setText("");
                    binding.etMacAddress.setText("");
                    
                } else {
                    String errMsg = "Khởi tạo thất bại";
                    try {
                        if (response.errorBody() != null) {
                            errMsg = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}

                    binding.tvStatus.setText(errMsg);
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light));
                    binding.tvStatus.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                binding.btnProvision.setEnabled(true);
                binding.btnProvision.setText("GENERATE TOKEN");
                binding.tvStatus.setText("Lỗi mạng: " + t.getMessage());
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light));
                binding.tvStatus.setVisibility(View.VISIBLE);
            }
        });
    }
}
