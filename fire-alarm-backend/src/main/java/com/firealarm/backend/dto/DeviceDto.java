package com.firealarm.backend.dto;

import com.firealarm.backend.entity.Device;
import lombok.Data;

@Data
public class DeviceDto {
    private String deviceId;
    private String name;
    private String status;
    private String macAddress;
    private String location;
    private Float mapX;
    private Float mapY;
    private String ownerUsername;

    public static DeviceDto fromEntity(Device d) {
        DeviceDto dto = new DeviceDto();
        dto.setDeviceId(d.getDeviceId());
        dto.setName(d.getName());
        dto.setStatus(d.getStatus().name());
        dto.setMacAddress(d.getMacAddress());
        dto.setLocation(d.getLocation());
        dto.setMapX(d.getMapX());
        dto.setMapY(d.getMapY());
        if (d.getOwner() != null) dto.setOwnerUsername(d.getOwner().getUsername());
        return dto;
    }
}
