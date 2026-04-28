package com.firealarm.backend.controller;

import com.firealarm.backend.dto.AlertHistoryDto;
import com.firealarm.backend.repository.AlertHistoryRepository;
import com.firealarm.backend.entity.Device;
import com.firealarm.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertHistoryRepository alertHistoryRepository;
    private final DeviceRepository deviceRepository;

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getAlertsByDevice(@PathVariable String deviceId, Authentication auth) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return ResponseEntity.notFound().build();

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && (device.getOwner() == null || !device.getOwner().getUsername().equals(auth.getName()))) {
            return ResponseEntity.status(403).body("Access Denied: Not your device");
        }

        List<AlertHistoryDto> result = alertHistoryRepository
                .findByDevice_DeviceIdOrderByTimestampDesc(deviceId)
                .stream()
                .map(AlertHistoryDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertHistoryDto>> getAllAlerts() {
        List<AlertHistoryDto> result = alertHistoryRepository
                .findAll()
                .stream()
                .map(AlertHistoryDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{alertId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resolveAlert(@PathVariable java.util.UUID alertId) {
        alertHistoryRepository.findById(alertId).ifPresent(alert -> {
            alert.setResolved(true);
            alertHistoryRepository.save(alert);
        });
        return ResponseEntity.ok().build();
    }
}
