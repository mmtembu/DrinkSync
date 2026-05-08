package com.smarteventbar.repository;

import com.smarteventbar.model.entity.SpiritItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpiritItemRepository extends JpaRepository<SpiritItem, Long> {

    List<SpiritItem> findByStationId(Long stationId);

    List<SpiritItem> findByStationIdAndAvailableTrue(Long stationId);
}
