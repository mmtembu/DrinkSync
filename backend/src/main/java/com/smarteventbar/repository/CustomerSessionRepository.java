package com.smarteventbar.repository;

import com.smarteventbar.model.entity.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, Long> {

    Optional<CustomerSession> findBySessionId(String sessionId);

    List<CustomerSession> findByStationIdAndExpiredFalse(Long stationId);

    List<CustomerSession> findByExpiredFalseAndLastActivityAtBefore(LocalDateTime cutoff);
}
