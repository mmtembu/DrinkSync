package com.smarteventbar.repository;

import com.smarteventbar.model.entity.OrderStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStateHistoryRepository extends JpaRepository<OrderStateHistory, Long> {

    List<OrderStateHistory> findByOrderId(Long orderId);
}
