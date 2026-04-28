package com.firealarm.backend.controller;

import com.firealarm.backend.dto.ClaimDeviceRequest;
import com.firealarm.backend.dto.DeviceDto;
import com.firealarm.backend.entity.Device;
import com.firealarm.backend.entity.User;
import com.firealarm.backend.entity.enums.DeviceStatus;
import com.firealarm.backend.repository.DeviceRepository;
import com.firealarm.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    /** Lấy tất cả thiết bị (admin) hoặc của user hiện tại */
    @GetMapping
    public ResponseEntity<List<DeviceDto>> getMyDevices(Authentication auth) {
        String username = auth.getName();
        List<DeviceDto> devices = deviceRepository.findByOwner_Username(username)
                .stream().map(DeviceDto::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(devices);
    }

    /** Lấy tất cả thiết bị UNCLAIMED (admin provision) */
    @GetMapping("/unclaimed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeviceDto>> getUnclaimed() {
        List<DeviceDto> devices = deviceRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeviceStatus.UNCLAIMED)
                .map(DeviceDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(devices);
    }

    /**
     * PROVISION — Admin/nhà sản xuất tạo thiết bị mới với claim token
     * POST /api/devices/provision
     * Body: { "deviceId": "DEV_001", "name": "Cảm biến tầng 1", "macAddress": "AA:BB:CC" }
     */
    @PostMapping("/provision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> provisionDevice(@RequestBody Device device) {
        if (deviceRepository.existsById(device.getDeviceId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Device ID already exists"));
        }
        // Tạo claimToken ngẫu nhiên dạng XXX-XXX-XXX
        String token = generateClaimToken();
        device.setClaimToken(token);
        device.setStatus(DeviceStatus.UNCLAIMED);
        Device saved = deviceRepository.save(device);
        log.info("Device provisioned: {} with token: {}", device.getDeviceId(), token);
        return ResponseEntity.ok(Map.of(
                "deviceId", saved.getDeviceId(),
                "claimToken", token,
                "message", "Device provisioned. Share claimToken with customer."
        ));
    }

    /**
     * CLAIM — Khách hàng nhập deviceId + claimToken để nhận thiết bị
     * POST /api/devices/claim
     * Body: { "deviceId": "DEV_001", "claimToken": "XK9-2A4-MNQ", "location": "Living Room" }
     */
    @PostMapping("/claim")
    public ResponseEntity<?> claimDevice(@RequestBody ClaimDeviceRequest req,
                                          Authentication auth) {
        Device device = deviceRepository.findById(req.getDeviceId())
                .orElse(null);

        if (device == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Device not found"));
        }
        if (device.getStatus() != DeviceStatus.UNCLAIMED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Device already claimed"));
        }
        if (!device.getClaimToken().equals(req.getClaimToken())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid claim token"));
        }

        User owner = userRepository.findByUsername(auth.getName()).orElseThrow();
        device.setOwner(owner);
        device.setStatus(DeviceStatus.ACTIVE);
        device.setClaimToken(null); // Xóa token sau khi dùng
        if (req.getLocation() != null) device.setLocation(req.getLocation());
        deviceRepository.save(device);

        log.info("Device {} claimed by user {}", device.getDeviceId(), auth.getName());
        return ResponseEntity.ok(Map.of(
                "message", "Device claimed successfully!",
                "device", DeviceDto.fromEntity(device)
        ));
    }

    /**
     * AUTO-REGISTER — Được gọi từ SensorProcessingService khi ESP32 gửi MQTT lần đầu
     * Tạo thiết bị UNCLAIMED tự động
     */
    @PostMapping("/auto-register")
    public ResponseEntity<?> autoRegister(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        if (deviceRepository.existsById(deviceId)) {
            return ResponseEntity.ok(Map.of("message", "Already exists"));
        }
        Device device = Device.builder()
                .deviceId(deviceId)
                .name("Sensor " + deviceId)
                .macAddress(deviceId) // Dùng deviceId tạm thời
                .status(DeviceStatus.UNCLAIMED)
                .claimToken(generateClaimToken())
                .build();
        deviceRepository.save(device);
        log.info("Auto-registered new device: {}", deviceId);
        return ResponseEntity.ok(Map.of("message", "Auto-registered", "deviceId", deviceId));
    }

    /** Xóa thiết bị (chỉ owner mới xóa được) */
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> deleteDevice(@PathVariable String deviceId, Authentication auth) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return ResponseEntity.notFound().build();
        if (device.getOwner() == null || !device.getOwner().getUsername().equals(auth.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your device"));
        }
        deviceRepository.delete(device);
        return ResponseEntity.ok(Map.of("message", "Device removed"));
    }

    /** Cập nhật tên/vị trí thiết bị */
    @PatchMapping("/{deviceId}")
    public ResponseEntity<?> updateDevice(@PathVariable String deviceId,
                                           @RequestBody Map<String, String> body,
                                           Authentication auth) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return ResponseEntity.notFound().build();

        if (body.containsKey("name")) device.setName(body.get("name"));
        if (body.containsKey("location")) device.setLocation(body.get("location"));
        deviceRepository.save(device);
        return ResponseEntity.ok(DeviceDto.fromEntity(device));
    }

    // ===== HELPER =====
    private String generateClaimToken() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return uuid.substring(0, 3) + "-" + uuid.substring(3, 6) + "-" + uuid.substring(6, 9);
    }
}
