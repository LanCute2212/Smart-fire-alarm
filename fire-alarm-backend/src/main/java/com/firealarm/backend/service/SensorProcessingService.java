package com.firealarm.backend.service;

import com.firealarm.backend.dto.SensorPayload;
import com.firealarm.backend.entity.SensorData;
import com.firealarm.backend.entity.AlertHistory;
import com.firealarm.backend.entity.enums.SeverityLevel;
import com.firealarm.backend.repository.AlertHistoryRepository;
import com.firealarm.backend.repository.DeviceRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorProcessingService {

    private final InfluxDBClient influxDBClient;
    private final DeviceRepository deviceRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // To track Rate of Change (EWMA / last temperature)
    private final Map<String, Double> lastTempMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTempTimeMap = new ConcurrentHashMap<>();

    public void processPayload(String deviceId, SensorPayload payload) {
        log.info("Processing payload for device {}: {}", deviceId, payload);

        long currentTime = System.currentTimeMillis();
        double currentTemp = payload.getTemperature() != null ? payload.getTemperature() : 0.0;

        // 1. Calculate Rate of Change (RoC) for Temperature
        double tempRocWeight = 0;
        if (lastTempMap.containsKey(deviceId)) {
            double lastTemp = lastTempMap.get(deviceId);
            long lastTime = lastTempTimeMap.get(deviceId);
            double timeDiffSecs = (currentTime - lastTime) / 1000.0;

            if (timeDiffSecs > 0) {
                double rate = (currentTemp - lastTemp) / timeDiffSecs;
                if (rate > 2.0) { // e.g. spikes 2 degree per second
                    tempRocWeight = 30; // heavy penalty for rapid spike
                    log.warn("Rapid temperature spike detected! Rate: {} degrees/s", rate);
                }
            }
        }

        // Update state
        lastTempMap.put(deviceId, currentTemp);
        lastTempTimeMap.put(deviceId, currentTime);

        // 2. Calculate Risk Score
        double riskScore = calculateRiskScore(payload, tempRocWeight);

        // 3. Save to InfluxDB
        SensorData data = new SensorData();
        data.setDeviceId(deviceId);
        data.setTemperature(payload.getTemperature());
        data.setHumidity(payload.getHumidity());
        data.setGasLevel(payload.getGasLevel());
        data.setIrFlameDetected(payload.getIrFlame() != null && payload.getIrFlame() == 1);
        data.setCalculatedRiskScore(riskScore);
        data.setTime(Instant.now());

        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            writeApi.writeMeasurement(WritePrecision.NS, data);
        } catch (Exception ex) {
            log.error("Failed to write to InfluxDB", ex);
        }

        // Push Raw Data via WebSocket for Live Graphing FrontEnd
        messagingTemplate.convertAndSend("/topic/sensors/" + deviceId, data);

        // 4. Trigger Alerts based on threshold
        if (riskScore >= 80) {
            triggerAlert(deviceId, riskScore, "CRITICAL: Fire risk extremely high. Score: " + riskScore);
        } else if (riskScore >= 50) {
            triggerAlert(deviceId, riskScore, "WARNING: Elevated risk factors detected. Score: " + riskScore);
        }
    }

    private double calculateRiskScore(SensorPayload payload, double tempRocWeight) {
        double score = 0;

        // IR Sensor (0 or 1). Very conclusive
        if (payload.getIrFlame() != null && payload.getIrFlame() == 1) {
            score += 50;
        }

        // Gas > 300 ppm
        if (payload.getGasLevel() != null && payload.getGasLevel() > 300) {
            score += 30;
        }

        // Steady Temperature > 50C
        if (payload.getTemperature() != null && payload.getTemperature() > 50) {
            score += 20;
        }

        // Add ROC weight
        score += tempRocWeight;

        return Math.min(score, 100.0); // Cap at 100
    }

    private void triggerAlert(String deviceId, double riskScore, String reason) {
        log.error("=> ALERT TRIGGERED FOR DEVICE {}: {}", deviceId, reason);
        // Push Alert Notification via WebSocket
        messagingTemplate.convertAndSend("/topic/alerts/" + deviceId, reason);

        deviceRepository.findById(deviceId).ifPresent(device -> {
            AlertHistory history = AlertHistory.builder()
                    .device(device)
                    .timestamp(LocalDateTime.now())
                    .severityLevel(riskScore >= 80 ? SeverityLevel.CRITICAL : SeverityLevel.WARNING)
                    .triggerReason(reason)
                    .resolved(false)
                    .build();
            alertHistoryRepository.save(history);
        });
    }
}
