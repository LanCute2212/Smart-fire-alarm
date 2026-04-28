package com.example.firealarmapp.models;

public class Device {
    private String deviceId;
    private String name;
    private String status;
    private String macAddress;
    private String location;
    private Float mapX;
    private Float mapY;
    private String ownerUsername;

    public String getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getMacAddress() { return macAddress; }
    public String getLocation() { return location; }
    public Float getMapX() { return mapX; }
    public Float getMapY() { return mapY; }
    public String getOwnerUsername() { return ownerUsername; }

    @Override
    public String toString() {
        return (name != null ? name : deviceId)
                + (location != null ? " (" + location + ")" : "");
    }
}
