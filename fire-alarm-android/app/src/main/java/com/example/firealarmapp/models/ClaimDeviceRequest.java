package com.example.firealarmapp.models;

public class ClaimDeviceRequest {
    private String deviceId;
    private String claimToken;
    private String location;

    public ClaimDeviceRequest(String deviceId, String claimToken, String location) {
        this.deviceId = deviceId;
        this.claimToken = claimToken;
        this.location = location;
    }

    public String getDeviceId() { return deviceId; }
    public String getClaimToken() { return claimToken; }
    public String getLocation() { return location; }
}
