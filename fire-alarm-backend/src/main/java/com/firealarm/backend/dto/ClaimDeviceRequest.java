package com.firealarm.backend.dto;

import lombok.Data;

@Data
public class ClaimDeviceRequest {
    private String deviceId;
    private String claimToken;
    private String location;  // "Living Room", "Kitchen", etc.
}
