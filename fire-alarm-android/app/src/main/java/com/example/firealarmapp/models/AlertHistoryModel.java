package com.example.firealarmapp.models;

import java.util.UUID;

public class AlertHistoryModel {
    private UUID alertId;
    private String deviceId;
    private String deviceName;
    private String timestamp;
    private String severityLevel;
    private String colorCode;
    private String triggerReason;
    private boolean resolved;

    public UUID getAlertId() { return alertId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getTimestamp() { return timestamp; }
    public String getSeverityLevel() { return severityLevel; }
    public String getColorCode() { return colorCode; }
    public String getTriggerReason() { return triggerReason; }
    public boolean isResolved() { return resolved; }
}
