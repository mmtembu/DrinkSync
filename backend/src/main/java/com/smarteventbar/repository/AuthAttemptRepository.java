package com.smarteventbar.repository;

import com.smarteventbar.model.entity.AuthAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, Long> {

    int countByStationIdAndSuccessFalseAndAttemptedAtAfter(Long stationId, LocalDateTime after);
}
