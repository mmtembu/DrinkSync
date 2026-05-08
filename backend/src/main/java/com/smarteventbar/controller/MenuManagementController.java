package com.smarteventbar.controller;

import com.smarteventbar.dto.MenuResponse;
import com.smarteventbar.dto.MixerItemRequest;
import com.smarteventbar.dto.PremadeItemRequest;
import com.smarteventbar.dto.SpiritItemRequest;
import com.smarteventbar.model.entity.MixerItem;
import com.smarteventbar.model.entity.PremadeItem;
import com.smarteventbar.model.entity.SpiritItem;
import com.smarteventbar.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stations/{stationId}")
public class MenuManagementController {

    private final MenuService menuService;

    public MenuManagementController(MenuService menuService) {
        this.menuService = menuService;
    }

    // --- Spirit Items ---

    @PostMapping("/spirits")
    public ResponseEntity<MenuResponse.SpiritItemDto> createSpirit(
            @PathVariable Long stationId,
            @Valid @RequestBody SpiritItemRequest request) {
        SpiritItem item = menuService.createSpirit(stationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MenuResponse.SpiritItemDto.fromEntity(item));
    }

    @PutMapping("/spirits/{itemId}")
    public ResponseEntity<MenuResponse.SpiritItemDto> updateSpirit(
            @PathVariable Long stationId,
            @PathVariable Long itemId,
            @Valid @RequestBody SpiritItemRequest request) {
        SpiritItem item = menuService.updateSpirit(stationId, itemId, request);
        return ResponseEntity.ok(MenuResponse.SpiritItemDto.fromEntity(item));
    }

    @DeleteMapping("/spirits/{itemId}")
    public ResponseEntity<Void> deleteSpirit(
            @PathVariable Long stationId,
            @PathVariable Long itemId) {
        menuService.deleteSpirit(stationId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/spirits/{itemId}/availability")
    public ResponseEntity<Void> toggleSpiritAvailability(
            @PathVariable Long stationId,
            @PathVariable Long itemId) {
        menuService.toggleSpiritAvailability(stationId, itemId);
        return ResponseEntity.ok().build();
    }

    // --- Mixer Items ---

    @PostMapping("/mixers")
    public ResponseEntity<MenuResponse.MixerItemDto> createMixer(
            @PathVariable Long stationId,
            @Valid @RequestBody MixerItemRequest request) {
        MixerItem item = menuService.createMixer(stationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MenuResponse.MixerItemDto.fromEntity(item));
    }

    @PutMapping("/mixers/{itemId}")
    public ResponseEntity<MenuResponse.MixerItemDto> updateMixer(
            @PathVariable Long stationId,
            @PathVariable Long itemId,
            @Valid @RequestBody MixerItemRequest request) {
        MixerItem item = menuService.updateMixer(stationId, itemId, request);
        return ResponseEntity.ok(MenuResponse.MixerItemDto.fromEntity(item));
    }

    @DeleteMapping("/mixers/{itemId}")
    public ResponseEntity<Void> deleteMixer(
            @PathVariable Long stationId,
            @PathVariable Long itemId) {
        menuService.deleteMixer(stationId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/mixers/{itemId}/availability")
    public ResponseEntity<Void> toggleMixerAvailability(
            @PathVariable Long stationId,
            @PathVariable Long itemId) {
        menuService.toggleMixerAvailability(stationId, itemId);
        return ResponseEntity.ok().build();
    }

    // --- Premade Items ---

    @PostMapping("/premades")
    public ResponseEntity<MenuResponse.PremadeItemDto> createPremade(
            @PathVariable Long stationId,
            @Valid @RequestBody PremadeItemRequest request) {
        PremadeItem item = menuService.createPremade(stationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MenuResponse.PremadeItemDto.fromEntity(item));
    }

    @PutMapping("/premades/{itemId}")
    public ResponseEntity<MenuResponse.PremadeItemDto> updatePremade(
            @PathVariable Long stationId,
            @PathVariable Long itemId,
            @Valid @RequestBody PremadeItemRequest request) {
        PremadeItem item = menuService.updatePremade(stationId, itemId, request);
        return ResponseEntity.ok(MenuResponse.PremadeItemDto.fromEntity(item));
    }

    @DeleteMapping("/premades/{itemId}")
    public ResponseEntity<Void> deletePremade(
            @PathVariable Long stationId,
            @PathVariable Long itemId) {
        menuService.deletePremade(stationId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/premades/{itemId}/availability")
    public ResponseEntity<Void> togglePremadeAvailability(
            @PathVariable Long stationId,
            @PathVariable Long itemId) {
        menuService.togglePremadeAvailability(stationId, itemId);
        return ResponseEntity.ok().build();
    }
}
