package com.firealarm.backend.controller;

import com.firealarm.backend.entity.Device;
import com.firealarm.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;

    @GetMapping
    public ResponseEntity<List<Device>> getAllDevices() {
        return ResponseEntity.ok(deviceRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Device> registerDevice(@RequestBody Device device) {
        return ResponseEntity.ok(deviceRepository.save(device));
    }
}
