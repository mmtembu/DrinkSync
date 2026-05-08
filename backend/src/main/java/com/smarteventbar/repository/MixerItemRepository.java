package com.smarteventbar.repository;

import com.smarteventbar.model.entity.MixerItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MixerItemRepository extends JpaRepository<MixerItem, Long> {

    List<MixerItem> findByStationId(Long stationId);

    List<MixerItem> findByStationIdAndAvailableTrue(Long stationId);
}
