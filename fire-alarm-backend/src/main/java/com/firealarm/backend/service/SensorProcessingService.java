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

    private final Map<String, Double> lastTempMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTempTimeMap = new ConcurrentHashMap<>();

    public void processPayload(String deviceId, SensorPayload payload) {
        log.info("Processing payload for device {}: {}", deviceId, payload);

        long currentTime = System.currentTimeMillis();
        double currentTemp = payload.getTemperature() != null ? payload.getTemperature() : 0.0;

        double tempRocWeight = 0;
        if (lastTempMap.containsKey(deviceId)) {
            double lastTemp = lastTempMap.get(deviceId);
            long lastTime = lastTempTimeMap.get(deviceId);
            double timeDiffSecs = (currentTime - lastTime) / 1000.0;

            if (timeDiffSecs > 0) {
                double rate = (currentTemp - lastTemp) / timeDiffSecs;
                if (rate > 2.0) {
                    tempRocWeight = 30;
                    log.warn("Rapid temperature spike detected! Rate: {} degrees/s", rate);
                }
            }
        }

        lastTempMap.put(deviceId, currentTemp);
        lastTempTimeMap.put(deviceId, currentTime);

        double riskScore = calculateRiskScore(payload, tempRocWeight);

        SensorData data = new SensorData();
        data.setDeviceId(deviceId);
        data.setTemperature(payload.getTemperature());
        data.setHumidity(payload.getHumidity());
        data.setGasLevel(payload.getGasLevel());
        data.setIrFlameDetected(payload.getIrFlame() != null && payload.getIrFlame() == 1);
        data.setCalculatedRiskScore(riskScore);
        data.setBatteryPercent(payload.getBatteryPercent() != null ? payload.getBatteryPercent() : 100);
        data.setTime(Instant.now());

        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            writeApi.writeMeasurement(WritePrecision.NS, data);
        } catch (Exception ex) {
            log.error("Failed to write to InfluxDB", ex);
        }

        messagingTemplate.convertAndSend("/topic/sensors/" + deviceId, data);

        if (riskScore >= 80) {
            triggerAlert(deviceId, riskScore, "CRITICAL: Fire risk extremely high. Score: " + riskScore);
        } else if (riskScore >= 50) {
            triggerAlert(deviceId, riskScore, "WARNING: Elevated risk factors detected. Score: " + riskScore);
        }
    }

    private double calculateRiskScore(SensorPayload payload, double tempRocWeight) {
        // Ưu tiên tuyệt đối: Thấy lửa là 100 điểm
        if (payload.getIrFlame() != null && payload.getIrFlame() == 1) {
            return 100.0;
        }

        double score = 0;

        // Logic chia bậc khí Gas đồng bộ với Arduino
        if (payload.getGasLevel() != null) {
            double gas = payload.getGasLevel();
            if (gas > 800 && gas <= 1500) {
                score += 30;
            } else if (gas > 1500 && gas < 3000) {
                score += 50;
            } else if (gas >= 3000) {
                score += 60;
            }
        }

        if (payload.getTemperature() != null && payload.getTemperature() > 50) {
            score += 20;
        }

        score += tempRocWeight;

        return Math.min(score, 100.0);
    }

    private void triggerAlert(String deviceId, double riskScore, String reason) {
        log.error("=> ALERT TRIGGERED FOR DEVICE {}: {}", deviceId, reason);
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
