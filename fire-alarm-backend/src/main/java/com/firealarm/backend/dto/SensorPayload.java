package com.firealarm.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SensorPayload {
    @JsonProperty("t")
    private Double temperature;

    @JsonProperty("h")
    private Double humidity;

    @JsonProperty("g")
    private Double gasLevel;

    @JsonProperty("ir")
    private Integer irFlame; // 1 means true, 0 means false
}
