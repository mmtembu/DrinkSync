package com.smarteventbar.service.impl;

import com.smarteventbar.dto.*;
import com.smarteventbar.model.entity.*;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.MenuService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MenuServiceImpl implements MenuService {

    private final StationRepository stationRepository;
    private final SpiritItemRepository spiritItemRepository;
    private final MixerItemRepository mixerItemRepository;
    private final PremadeItemRepository premadeItemRepository;

    public MenuServiceImpl(StationRepository stationRepository,
                           SpiritItemRepository spiritItemRepository,
                           MixerItemRepository mixerItemRepository,
                           PremadeItemRepository premadeItemRepository) {
        this.stationRepository = stationRepository;
        this.spiritItemRepository = spiritItemRepository;
        this.mixerItemRepository = mixerItemRepository;
        this.premadeItemRepository = premadeItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MenuResponse getMenuForStation(Long stationId) {
        Station station = findStation(stationId);

        List<MenuResponse.SpiritItemDto> spirits = spiritItemRepository.findByStationId(stationId)
                .stream().map(MenuResponse.SpiritItemDto::fromEntity).toList();
        List<MenuResponse.MixerItemDto> mixers = mixerItemRepository.findByStationId(stationId)
                .stream().map(MenuResponse.MixerItemDto::fromEntity).toList();
        List<MenuResponse.PremadeItemDto> premades = premadeItemRepository.findByStationId(stationId)
                .stream().map(MenuResponse.PremadeItemDto::fromEntity).toList();

        MenuResponse response = new MenuResponse();
        response.setStationId(station.getId());
        response.setStationName(station.getName());
        response.setCupPrice(station.getCupPrice());
        response.setSpirits(spirits);
        response.setMixers(mixers);
        response.setPremades(premades);
        return response;
    }

    // --- Spirit Items ---

    @Override
    @Transactional
    public SpiritItem createSpirit(Long stationId, SpiritItemRequest request) {
        Station station = findStation(stationId);
        SpiritItem item = new SpiritItem(station, request.getName(), request.getPrice(), true);
        return spiritItemRepository.save(item);
    }

    @Override
    @Transactional
    public SpiritItem updateSpirit(Long stationId, Long itemId, SpiritItemRequest request) {
        SpiritItem item = findSpiritItem(stationId, itemId);
        item.setName(request.getName());
        item.setPrice(request.getPrice());
        return spiritItemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteSpirit(Long stationId, Long itemId) {
        SpiritItem item = findSpiritItem(stationId, itemId);
        spiritItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void toggleSpiritAvailability(Long stationId, Long itemId) {
        SpiritItem item = findSpiritItem(stationId, itemId);
        item.setAvailable(!item.isAvailable());
        spiritItemRepository.save(item);
    }

    // --- Mixer Items ---

    @Override
    @Transactional
    public MixerItem createMixer(Long stationId, MixerItemRequest request) {
        Station station = findStation(stationId);
        MixerItem item = new MixerItem(station, request.getName(), request.getPrice(), true);
        return mixerItemRepository.save(item);
    }

    @Override
    @Transactional
    public MixerItem updateMixer(Long stationId, Long itemId, MixerItemRequest request) {
        MixerItem item = findMixerItem(stationId, itemId);
        item.setName(request.getName());
        item.setPrice(request.getPrice());
        return mixerItemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteMixer(Long stationId, Long itemId) {
        MixerItem item = findMixerItem(stationId, itemId);
        mixerItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void toggleMixerAvailability(Long stationId, Long itemId) {
        MixerItem item = findMixerItem(stationId, itemId);
        item.setAvailable(!item.isAvailable());
        mixerItemRepository.save(item);
    }

    // --- Premade Items ---

    @Override
    @Transactional
    public PremadeItem createPremade(Long stationId, PremadeItemRequest request) {
        Station station = findStation(stationId);
        PremadeItem item = new PremadeItem(station, request.getName(), request.getDescription(),
                request.getPrice(), true);
        return premadeItemRepository.save(item);
    }

    @Override
    @Transactional
    public PremadeItem updatePremade(Long stationId, Long itemId, PremadeItemRequest request) {
        PremadeItem item = findPremadeItem(stationId, itemId);
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        return premadeItemRepository.save(item);
    }

    @Override
    @Transactional
    public void deletePremade(Long stationId, Long itemId) {
        PremadeItem item = findPremadeItem(stationId, itemId);
        premadeItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void togglePremadeAvailability(Long stationId, Long itemId) {
        PremadeItem item = findPremadeItem(stationId, itemId);
        item.setAvailable(!item.isAvailable());
        premadeItemRepository.save(item);
    }

    // --- Station Settings ---

    @Override
    @Transactional
    public void setCupPrice(Long stationId, BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cup price must be zero or greater");
        }
        Station station = findStation(stationId);
        station.setCupPrice(price);
        stationRepository.save(station);
    }

    @Override
    @Transactional
    public void setPickupWindowMinutes(Long stationId, int minutes) {
        if (minutes < 5 || minutes > 30) {
            throw new IllegalArgumentException("Pickup window must be between 5 and 30 minutes");
        }
        Station station = findStation(stationId);
        station.setPickupWindowMinutes(minutes);
        stationRepository.save(station);
    }

    // --- Helpers ---

    private Station findStation(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new EntityNotFoundException("Station not found: " + stationId));
    }

    private SpiritItem findSpiritItem(Long stationId, Long itemId) {
        SpiritItem item = spiritItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Spirit item not found: " + itemId));
        if (!item.getStation().getId().equals(stationId)) {
            throw new IllegalArgumentException("Spirit item does not belong to station " + stationId);
        }
        return item;
    }

    private MixerItem findMixerItem(Long stationId, Long itemId) {
        MixerItem item = mixerItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Mixer item not found: " + itemId));
        if (!item.getStation().getId().equals(stationId)) {
            throw new IllegalArgumentException("Mixer item does not belong to station " + stationId);
        }
        return item;
    }

    private PremadeItem findPremadeItem(Long stationId, Long itemId) {
        PremadeItem item = premadeItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Premade item not found: " + itemId));
        if (!item.getStation().getId().equals(stationId)) {
            throw new IllegalArgumentException("Premade item does not belong to station " + stationId);
        }
        return item;
    }
}
