package com.smarteventbar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VendorLoginRequest {

    @NotNull
    private Long stationId;

    @NotBlank
    private String accessCode;

    public VendorLoginRequest() {
    }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public String getAccessCode() { return accessCode; }
    public void setAccessCode(String accessCode) { this.accessCode = accessCode; }
}
