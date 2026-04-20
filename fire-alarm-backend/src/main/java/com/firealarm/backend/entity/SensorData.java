package com.firealarm.backend.entity;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;
import java.time.Instant;

@Data
@Measurement(name = "sensor_data")
public class SensorData {

    @Column(tag = true)
    private String deviceId;

    @Column
    private Double temperature;

    @Column
    private Double humidity;

    @Column
    private Double gasLevel;

    @Column
    private Boolean irFlameDetected;

    @Column
    private Double calculatedRiskScore;

    @Column(timestamp = true)
    private Instant time;
}
