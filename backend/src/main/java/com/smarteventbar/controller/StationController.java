package com.smarteventbar.controller;

import com.smarteventbar.dto.MenuResponse;
import com.smarteventbar.dto.StationResponse;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.StationRepository;
import com.smarteventbar.service.MenuService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationRepository stationRepository;
    private final MenuService menuService;

    public StationController(StationRepository stationRepository, MenuService menuService) {
        this.stationRepository = stationRepository;
        this.menuService = menuService;
    }

    @GetMapping("/{stationId}")
    public ResponseEntity<StationResponse> getStation(@PathVariable Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new EntityNotFoundException("Station not found: " + stationId));
        return ResponseEntity.ok(StationResponse.fromEntity(station));
    }

    @GetMapping("/{stationId}/menu")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable Long stationId) {
        return ResponseEntity.ok(menuService.getMenuForStation(stationId));
    }

    @PutMapping("/{stationId}/cup-price")
    public ResponseEntity<Void> setCupPrice(@PathVariable Long stationId,
                                            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal price = body.get("cupPrice");
        if (price == null) {
            throw new IllegalArgumentException("cupPrice is required");
        }
        menuService.setCupPrice(stationId, price);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{stationId}/pickup-window")
    public ResponseEntity<Void> setPickupWindow(@PathVariable Long stationId,
                                                @RequestBody Map<String, Integer> body) {
        Integer minutes = body.get("pickupWindowMinutes");
        if (minutes == null) {
            throw new IllegalArgumentException("pickupWindowMinutes is required");
        }
        menuService.setPickupWindowMinutes(stationId, minutes);
        return ResponseEntity.ok().build();
    }
}
