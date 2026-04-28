package com.firealarm.backend.dto;

import com.firealarm.backend.entity.AlertHistory;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AlertHistoryDto {
    private UUID alertId;
    private String deviceId;
    private String deviceName;
    private LocalDateTime timestamp;
    private String severityLevel;
    private String colorCode;
    private String triggerReason;
    private boolean resolved;

    public static AlertHistoryDto fromEntity(AlertHistory a) {
        AlertHistoryDto dto = new AlertHistoryDto();
        dto.setAlertId(a.getAlertId());
        dto.setDeviceId(a.getDevice().getDeviceId());
        dto.setDeviceName(a.getDevice().getName());
        dto.setTimestamp(a.getTimestamp());
        dto.setSeverityLevel(a.getSeverityLevel().name());
        dto.setColorCode(a.getSeverityLevel().getColorCode());
        dto.setTriggerReason(a.getTriggerReason());
        dto.setResolved(a.isResolved());
        return dto;
    }
}
