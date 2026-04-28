package com.firealarm.backend.entity.enums;

import lombok.Getter;

@Getter
public enum SeverityLevel {
    NORMAL("#4CAF50"),
    WARNING("#FFC107"),
    CRITICAL("#F44336");

    private final String colorCode;

    SeverityLevel(String colorCode) {
        this.colorCode = colorCode;
    }
}
