package com.smarteventbar.dto;

import com.smarteventbar.model.entity.Station;

import java.math.BigDecimal;

public class StationResponse {

    private Long id;
    private String name;
    private String locationDescription;
    private BigDecimal cupPrice;
    private int pickupWindowMinutes;

    public StationResponse() {
    }

    public static StationResponse fromEntity(Station station) {
        StationResponse r = new StationResponse();
        r.setId(station.getId());
        r.setName(station.getName());
        r.setLocationDescription(station.getLocationDescription());
        r.setCupPrice(station.getCupPrice());
        r.setPickupWindowMinutes(station.getPickupWindowMinutes());
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
    public BigDecimal getCupPrice() { return cupPrice; }
    public void setCupPrice(BigDecimal cupPrice) { this.cupPrice = cupPrice; }
    public int getPickupWindowMinutes() { return pickupWindowMinutes; }
    public void setPickupWindowMinutes(int pickupWindowMinutes) { this.pickupWindowMinutes = pickupWindowMinutes; }
}
