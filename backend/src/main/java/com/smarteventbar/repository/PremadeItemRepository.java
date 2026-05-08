package com.smarteventbar.repository;

import com.smarteventbar.model.entity.PremadeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PremadeItemRepository extends JpaRepository<PremadeItem, Long> {

    List<PremadeItem> findByStationId(Long stationId);

    List<PremadeItem> findByStationIdAndAvailableTrue(Long stationId);
}
