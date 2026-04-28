package com.example.firealarmapp.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firealarmapp.api.ApiService;
import com.example.firealarmapp.databinding.ActivityAlertHistoryBinding;
import com.example.firealarmapp.models.AlertHistoryModel;
import com.example.firealarmapp.utils.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlertHistoryActivity extends AppCompatActivity {

    private ActivityAlertHistoryBinding binding;
    private ApiService apiService;
    private AlertAdapter alertAdapter;
    private List<AlertHistoryModel> alerts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAlertHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String deviceId = getIntent().getStringExtra("deviceId");
        String deviceName = getIntent().getStringExtra("deviceName");

        binding.tvDeviceLabel.setText("Device: " + deviceName + " (" + deviceId + ")");
        binding.btnBack.setOnClickListener(v -> finish());

        apiService = RetrofitClient.getApiService(this);

        alertAdapter = new AlertAdapter(alerts, apiService);
        binding.recyclerAlerts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAlerts.setAdapter(alertAdapter);

        loadAlerts(deviceId);
    }

    private void loadAlerts(String deviceId) {
        binding.progressBar.setVisibility(View.VISIBLE);
        apiService.getAlertHistory(deviceId).enqueue(new Callback<List<AlertHistoryModel>>() {
            @Override
            public void onResponse(Call<List<AlertHistoryModel>> call, Response<List<AlertHistoryModel>> response) {
                binding.progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    alerts.clear();
                    alerts.addAll(response.body());
                    alertAdapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(alerts.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<List<AlertHistoryModel>> call, Throwable t) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(AlertHistoryActivity.this, "Failed to load alerts", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Inner RecyclerView Adapter ---
    static class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.ViewHolder> {
        private final List<AlertHistoryModel> data;
        private final ApiService apiService;

        AlertAdapter(List<AlertHistoryModel> data, ApiService apiService) {
            this.data = data;
            this.apiService = apiService;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    com.example.firealarmapp.R.layout.item_alert, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AlertHistoryModel a = data.get(position);

            // Severity badge color
            holder.tvSeverity.setText(a.getSeverityLevel());
            if (a.getColorCode() != null && !a.getColorCode().isEmpty()) {
                try {
                    holder.tvSeverity.setBackgroundColor(Color.parseColor(a.getColorCode()));
                } catch (IllegalArgumentException e) {
                    holder.tvSeverity.setBackgroundColor(Color.parseColor("#64748b"));
                }
            } else {
                holder.tvSeverity.setBackgroundColor(Color.parseColor("#64748b"));
            }

            // Format timestamp (simple substring to remove T and nanoseconds)
            String ts = a.getTimestamp() != null ? a.getTimestamp().replace("T", " ") : "--";
            if (ts.length() > 16) ts = ts.substring(0, 16);
            holder.tvTimestamp.setText(ts);

            holder.tvReason.setText(a.getTriggerReason());

            if (a.isResolved()) {
                holder.tvResolved.setText("✓ Resolved");
                holder.tvResolved.setTextColor(Color.parseColor("#10b981"));
                holder.btnResolve.setVisibility(View.GONE);
            } else {
                holder.tvResolved.setText("⚠ Unresolved");
                holder.tvResolved.setTextColor(Color.parseColor("#f59e0b"));
                holder.btnResolve.setVisibility(View.VISIBLE);
                holder.btnResolve.setOnClickListener(v -> {
                    apiService.resolveAlert(a.getAlertId().toString()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            data.set(holder.getAdapterPosition(),
                                new AlertHistoryModel() {{
                                    // Rebuild resolved copy - just update UI
                                }});
                            holder.tvResolved.setText("✓ Resolved");
                            holder.tvResolved.setTextColor(Color.parseColor("#10b981"));
                            holder.btnResolve.setVisibility(View.GONE);
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {}
                    });
                });
            }
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSeverity, tvTimestamp, tvReason, tvResolved;
            Button btnResolve;
            ViewHolder(View v) {
                super(v);
                tvSeverity  = v.findViewById(com.example.firealarmapp.R.id.tvSeverity);
                tvTimestamp = v.findViewById(com.example.firealarmapp.R.id.tvTimestamp);
                tvReason    = v.findViewById(com.example.firealarmapp.R.id.tvReason);
                tvResolved  = v.findViewById(com.example.firealarmapp.R.id.tvResolved);
                btnResolve  = v.findViewById(com.example.firealarmapp.R.id.btnResolve);
            }
        }
    }
}
