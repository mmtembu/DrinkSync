package com.smarteventbar.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "station")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "location_description")
    private String locationDescription;

    @Column(name = "cup_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal cupPrice;

    @Column(name = "access_code", nullable = false, unique = true, length = 6)
    private String accessCode;

    @Column(name = "pickup_window_minutes", nullable = false)
    private int pickupWindowMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Station() {
    }

    public Station(String name, String locationDescription, BigDecimal cupPrice, String accessCode, int pickupWindowMinutes) {
        this.name = name;
        this.locationDescription = locationDescription;
        this.cupPrice = cupPrice;
        this.accessCode = accessCode;
        this.pickupWindowMinutes = pickupWindowMinutes;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public BigDecimal getCupPrice() {
        return cupPrice;
    }

    public void setCupPrice(BigDecimal cupPrice) {
        this.cupPrice = cupPrice;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public int getPickupWindowMinutes() {
        return pickupWindowMinutes;
    }

    public void setPickupWindowMinutes(int pickupWindowMinutes) {
        this.pickupWindowMinutes = pickupWindowMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
