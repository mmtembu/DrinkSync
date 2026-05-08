package com.smarteventbar.repository;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.enums.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByStationIdAndState(Long stationId, OrderState state);

    List<CustomerOrder> findBySessionSessionId(String sessionId);

    List<CustomerOrder> findBySessionSessionIdAndState(String sessionId, OrderState state);

    List<CustomerOrder> findBySessionSessionIdAndStateIn(String sessionId, Collection<OrderState> states);

    List<CustomerOrder> findByStationIdAndStateIn(Long stationId, Collection<OrderState> states);

    int countBySessionSessionIdAndStateIn(String sessionId, Collection<OrderState> states);

    @Query("SELECT COALESCE(MAX(o.queuePosition), 0) FROM CustomerOrder o WHERE o.station.id = :stationId")
    int findMaxQueuePositionByStationId(@Param("stationId") Long stationId);

    List<CustomerOrder> findByState(OrderState state);
}
