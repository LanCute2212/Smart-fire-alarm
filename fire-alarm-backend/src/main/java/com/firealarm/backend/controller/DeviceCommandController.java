package com.firealarm.backend.controller;

import com.firealarm.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceCommandController {

    private final MessageChannel mqttOutboundChannel;
    private final DeviceRepository deviceRepository;

    @PostMapping("/{deviceId}/silence")
    public ResponseEntity<String> silenceAlarm(@PathVariable String deviceId,
                                               @RequestParam(defaultValue = "30") int durationSeconds) {
        if (!deviceRepository.existsById(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String topic = "fire-alarm/command/" + deviceId;
            String payload = String.format("{\"action\":\"SILENCE_ALARM\",\"duration\":%d}", durationSeconds);
            mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build()
            );
            log.info("Silence command sent to device {}", deviceId);
            return ResponseEntity.ok("Silence command sent to device " + deviceId);
        } catch (Exception e) {
            log.error("Failed to send command", e);
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }
}
