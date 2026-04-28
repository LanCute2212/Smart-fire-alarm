package com.example.firealarmapp.activities;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.firealarmapp.api.ApiService;
import com.example.firealarmapp.databinding.ActivityDashboardBinding;
import com.example.firealarmapp.models.Device;
import com.example.firealarmapp.utils.RetrofitClient;
import com.example.firealarmapp.utils.SessionManager;
import com.example.firealarmapp.views.FloorMapView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    
    private ActivityDashboardBinding binding;
    private SessionManager sessionManager;
    private ApiService apiService;

    private StompClient mStompClient;
    private CompositeDisposable compositeDisposable;

    private List<Device> deviceList = new ArrayList<>();
    private Device selectedDevice;

    private List<Entry> tempEntries = new ArrayList<>();
    private List<Entry> humidEntries = new ArrayList<>();
    private int timeIndex = 0;

    // Status constants
    private static final int STATUS_SAFE = 0;
    private static final int STATUS_WARNING = 1;
    private static final int STATUS_DANGER = 2;
    private int currentStatus = STATUS_SAFE;

    private ObjectAnimator dangerFlashAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        apiService = RetrofitClient.getApiService(this);
        compositeDisposable = new CompositeDisposable();

        setupChart();
        setupFloorMap();

        // Role-based UI
        String role = sessionManager.getRole();
        if ("ADMIN".equals(role)) {
            binding.tvRoleBadge.setText("ADMIN MODE");
            binding.tvRoleBadge.setTextColor(Color.parseColor("#ef4444"));
            binding.tvRoleBadge.setBackgroundColor(Color.parseColor("#450a0a"));
            binding.btnAddDevice.setText("Provision");
            binding.btnAddDevice.setBackgroundResource(com.example.firealarmapp.R.drawable.bg_button_danger);
        } else {
            binding.tvRoleBadge.setText("USER MODE");
            binding.tvRoleBadge.setTextColor(Color.parseColor("#3b82f6"));
            binding.tvRoleBadge.setBackgroundColor(Color.parseColor("#172554"));
            binding.btnAddDevice.setText("+ Add");
            binding.btnAddDevice.setBackgroundResource(com.example.firealarmapp.R.drawable.bg_button_primary);
        }

        // Logout
        binding.btnLogout.setOnClickListener(v -> {
            sessionManager.clear();
            if (mStompClient != null) mStompClient.disconnect();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Add Device / Provision
        binding.btnAddDevice.setOnClickListener(v -> {
            if ("ADMIN".equals(sessionManager.getRole())) {
                startActivity(new Intent(this, AdminProvisionActivity.class));
            } else {
                startActivityForResult(
                        new Intent(this, AddDeviceActivity.class), 100);
            }
        });

        // History
        binding.btnHistory.setOnClickListener(v -> {
            if (selectedDevice == null) {
                Toast.makeText(this, "Please select a device first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, AlertHistoryActivity.class);
            intent.putExtra("deviceId", selectedDevice.getDeviceId());
            intent.putExtra("deviceName", selectedDevice.getName());
            startActivity(intent);
        });

        // Quick Action — Silence Alarm
        binding.btnSilence.setOnClickListener(v -> {
            if (selectedDevice == null) return;
            apiService.silenceAlarm(selectedDevice.getDeviceId(), 30).enqueue(new Callback<String>() {
                @Override
                public void onResponse(Call<String> call, Response<String> response) {
                    Toast.makeText(DashboardActivity.this, "Alarm silenced for 30s", Toast.LENGTH_SHORT).show();
                    binding.btnSilence.setEnabled(false);
                    binding.btnSilence.setText("🔇 Silenced...");
                    // Re-enable after 30s
                    binding.btnSilence.postDelayed(() -> {
                        binding.btnSilence.setEnabled(true);
                        binding.btnSilence.setText("🔇  Silence Alarm (30s)");
                    }, 30_000);
                }
                @Override
                public void onFailure(Call<String> call, Throwable t) {
                    Toast.makeText(DashboardActivity.this, "Failed to send command", Toast.LENGTH_SHORT).show();
                }
            });
        });

        fetchDevices();
    }

    private void setupChart() {
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.setDrawGridBackground(false);
        binding.lineChart.getAxisRight().setEnabled(false);
        binding.lineChart.setBackgroundColor(Color.parseColor("#1e293b"));

        XAxis xAxis = binding.lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setGridColor(Color.parseColor("#334155"));

        binding.lineChart.getAxisLeft().setTextColor(Color.WHITE);
        binding.lineChart.getAxisLeft().setGridColor(Color.parseColor("#334155"));
        binding.lineChart.getLegend().setTextColor(Color.WHITE);
    }

    private void setupFloorMap() {
        // Default: place currently selected device in the center of living room
        // In a real app this would come from DB
    }

    private void fetchDevices() {
        apiService.getDevices().enqueue(new Callback<List<Device>>() {
            @Override
            public void onResponse(Call<List<Device>> call, Response<List<Device>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    deviceList = response.body();
                    ArrayAdapter<Device> adapter = new ArrayAdapter<>(DashboardActivity.this,
                            android.R.layout.simple_spinner_item, deviceList);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    binding.spinnerDevices.setAdapter(adapter);

                    binding.spinnerDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedDevice = deviceList.get(position);
                            refreshFloorMap();
                            connectWebSocket();
                        }
                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
            }
            @Override
            public void onFailure(Call<List<Device>> call, Throwable t) {
                Toast.makeText(DashboardActivity.this, "Failed to load devices", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshFloorMap() {
        if (selectedDevice == null) return;
        List<FloorMapView.SensorPin> pins = new ArrayList<>();
        // Place device pin at a default position (Living Room center)
        // In production this would come from device metadata in DB
        pins.add(new FloorMapView.SensorPin(
                selectedDevice.getDeviceId(),
                selectedDevice.getName(),
                0.25f, 0.35f,  // Living Room
                STATUS_SAFE
        ));
        binding.floorMapView.setSensorPins(pins);
    }

    private void connectWebSocket() {
        if (mStompClient != null && mStompClient.isConnected()) {
            mStompClient.disconnect();
        }
        if (compositeDisposable != null) compositeDisposable.dispose();
        compositeDisposable = new CompositeDisposable();

        // Reset chart
        timeIndex = 0;
        tempEntries.clear();
        humidEntries.clear();
        binding.lineChart.clear();

        String wsUrl = "ws://172.17.171.250:8080/ws-firealarm/websocket";
        mStompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);

        List<StompHeader> headers = new ArrayList<>();
        headers.add(new StompHeader("Authorization", "Bearer " + sessionManager.getToken()));

        compositeDisposable.add(mStompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lc -> Log.d(TAG, "STOMP: " + lc.getType().name())));

        mStompClient.connect(headers);

        // Subscribe sensor data
        compositeDisposable.add(mStompClient.topic("/topic/sensors/" + selectedDevice.getDeviceId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(msg -> parseSensorData(msg.getPayload()),
                           err -> Log.e(TAG, "Sensor sub error", err)));

        // Subscribe alerts
        compositeDisposable.add(mStompClient.topic("/topic/alerts/" + selectedDevice.getDeviceId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(msg -> triggerDangerUI(msg.getPayload()),
                           err -> Log.e(TAG, "Alert sub error", err)));
    }

    private void parseSensorData(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            double temp = json.optDouble("temperature", 0);
            double humid = json.optDouble("humidity", 0);
            double gas = json.optDouble("gasLevel", 0);
            double risk = json.optDouble("calculatedRiskScore", 0);
            int battery = json.optInt("batteryPercent", 100);

            // Update sensor tiles
            binding.tvTemp.setText(String.format("%.1f°C", temp));
            binding.tvHumid.setText(String.format("%.1f%%", humid));
            binding.tvGas.setText(String.format("%.0f ppm", gas));
            binding.tvRisk.setText(String.format("%.0f", risk));
            binding.tvLastSeen.setText("Last update: " +
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));

            // Battery
            updateBattery(battery);

            // Status card
            updateStatusCard(risk);

            // Chart
            timeIndex++;
            tempEntries.add(new Entry(timeIndex, (float) temp));
            humidEntries.add(new Entry(timeIndex, (float) humid));
            if (tempEntries.size() > 30) { tempEntries.remove(0); humidEntries.remove(0); }

            LineDataSet tempSet = new LineDataSet(new ArrayList<>(tempEntries), "Temp °C");
            tempSet.setColor(Color.parseColor("#f59e0b"));
            tempSet.setDrawCircles(false);
            tempSet.setLineWidth(2f);

            LineDataSet humidSet = new LineDataSet(new ArrayList<>(humidEntries), "Humidity %");
            humidSet.setColor(Color.parseColor("#3b82f6"));
            humidSet.setDrawCircles(false);
            humidSet.setLineWidth(2f);

            binding.lineChart.setData(new LineData(tempSet, humidSet));
            binding.lineChart.invalidate();

        } catch (JSONException e) {
            Log.e(TAG, "JSON parse error", e);
        }
    }

    private void updateBattery(int percent) {
        binding.tvBattery.setText(percent + "%");
        if (percent > 50) binding.tvBattery.setTextColor(Color.parseColor("#10b981"));
        else if (percent > 20) binding.tvBattery.setTextColor(Color.parseColor("#f59e0b"));
        else binding.tvBattery.setTextColor(Color.parseColor("#ef4444"));
    }

    private void updateStatusCard(double riskScore) {
        if (riskScore >= 80) {
            if (currentStatus != STATUS_DANGER) {
                currentStatus = STATUS_DANGER;
                binding.statusCard.setBackgroundResource(com.example.firealarmapp.R.drawable.bg_status_danger);
                binding.tvStatusIcon.setText("🔥");
                binding.tvStatusText.setText("⚠ FIRE DANGER — EVACUATE");
                binding.tvRisk.setTextColor(Color.parseColor("#ef4444"));
                binding.btnSilence.setVisibility(View.VISIBLE);
                binding.floorMapView.updatePinStatus(
                        selectedDevice != null ? selectedDevice.getDeviceId() : "", STATUS_DANGER);
                vibrateDevice();
            }
        } else if (riskScore >= 50) {
            currentStatus = STATUS_WARNING;
            binding.statusCard.setBackgroundResource(com.example.firealarmapp.R.drawable.bg_status_warning);
            binding.tvStatusIcon.setText("⚠️");
            binding.tvStatusText.setText("WARNING — Elevated Risk");
            binding.tvRisk.setTextColor(Color.parseColor("#f59e0b"));
            binding.btnSilence.setVisibility(View.VISIBLE);
            binding.floorMapView.updatePinStatus(
                    selectedDevice != null ? selectedDevice.getDeviceId() : "", STATUS_WARNING);
        } else {
            currentStatus = STATUS_SAFE;
            binding.statusCard.setBackgroundResource(com.example.firealarmapp.R.drawable.bg_status_safe);
            binding.tvStatusIcon.setText("✅");
            binding.tvStatusText.setText("ALL CLEAR — SAFE");
            binding.tvRisk.setTextColor(Color.parseColor("#10b981"));
            binding.btnSilence.setVisibility(View.GONE);
            binding.floorMapView.updatePinStatus(
                    selectedDevice != null ? selectedDevice.getDeviceId() : "", STATUS_SAFE);
        }
    }

    private void triggerDangerUI(String alertMessage) {
        updateStatusCard(100); // force DANGER
        Toast.makeText(this, "🔥 ALERT: " + alertMessage, Toast.LENGTH_LONG).show();
    }

    private void vibrateDevice() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            long[] pattern = {0, 400, 200, 400, 200, 400};
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Refresh device list after adding new device
            fetchDevices();
            Toast.makeText(this, "Device list updated!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (mStompClient != null) mStompClient.disconnect();
        if (compositeDisposable != null) compositeDisposable.dispose();
        super.onDestroy();
    }
}
