package com.firealarm.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SensorPayload {
    @JsonProperty("t")
    @JsonAlias({"t", "temperature"})
    private Double temperature;

    @JsonProperty("h")
    @JsonAlias({"h", "humidity"})
    private Double humidity;

    @JsonProperty("g")
    @JsonAlias({"g", "gasLevel"})
    private Double gasLevel;

    @JsonProperty("ir")
    @JsonAlias({"ir", "irFlame"})
    private Integer irFlame; // 1 means true, 0 means false

    @JsonProperty("bat")
    @JsonAlias({"bat", "batteryPercent"})
    private Integer batteryPercent; // 0-100%
}
