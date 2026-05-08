package com.smarteventbar.service;

import com.smarteventbar.dto.MenuResponse;
import com.smarteventbar.dto.MixerItemRequest;
import com.smarteventbar.dto.PremadeItemRequest;
import com.smarteventbar.dto.SpiritItemRequest;
import com.smarteventbar.model.entity.MixerItem;
import com.smarteventbar.model.entity.PremadeItem;
import com.smarteventbar.model.entity.SpiritItem;

import java.math.BigDecimal;

public interface MenuService {

    MenuResponse getMenuForStation(Long stationId);

    SpiritItem createSpirit(Long stationId, SpiritItemRequest request);
    SpiritItem updateSpirit(Long stationId, Long itemId, SpiritItemRequest request);
    void deleteSpirit(Long stationId, Long itemId);
    void toggleSpiritAvailability(Long stationId, Long itemId);

    MixerItem createMixer(Long stationId, MixerItemRequest request);
    MixerItem updateMixer(Long stationId, Long itemId, MixerItemRequest request);
    void deleteMixer(Long stationId, Long itemId);
    void toggleMixerAvailability(Long stationId, Long itemId);

    PremadeItem createPremade(Long stationId, PremadeItemRequest request);
    PremadeItem updatePremade(Long stationId, Long itemId, PremadeItemRequest request);
    void deletePremade(Long stationId, Long itemId);
    void togglePremadeAvailability(Long stationId, Long itemId);

    void setCupPrice(Long stationId, BigDecimal price);
    void setPickupWindowMinutes(Long stationId, int minutes);
}
