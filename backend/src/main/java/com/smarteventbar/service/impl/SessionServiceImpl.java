package com.smarteventbar.service.impl;

import com.smarteventbar.exception.MaxConcurrentOrdersException;
import com.smarteventbar.exception.SessionExpiredException;
import com.smarteventbar.exception.SessionNotFoundException;
import com.smarteventbar.exception.StationLockException;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.OrderStateHistory;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.model.enums.TransitionTrigger;
import com.smarteventbar.repository.CustomerSessionRepository;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.repository.StationRepository;
import com.smarteventbar.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    private static final int SESSION_EXPIRY_HOURS = 2;
    private static final int MAX_CONCURRENT_ORDERS = 3;

    private static final Set<OrderState> ACTIVE_STATES = EnumSet.of(
            OrderState.DRAFT,
            OrderState.AWAITING_PAYMENT,
            OrderState.PAID,
            OrderState.PREPARING,
            OrderState.READY
    );

    private final CustomerSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final StationRepository stationRepository;

    public SessionServiceImpl(CustomerSessionRepository sessionRepository,
                              OrderRepository orderRepository,
                              StationRepository stationRepository) {
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    @Transactional
    public CustomerSession createSession(Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));

        String sessionId = UUID.randomUUID().toString();
        CustomerSession session = new CustomerSession(sessionId, station);
        session = sessionRepository.save(session);

        MDC.put("sessionId", sessionId);
        MDC.put("stationId", stationId.toString());
        log.info("Session created: sessionId={} stationId={}", sessionId, stationId);

        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSession getSession(String sessionId) {
        CustomerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.isExpired()) {
            throw new SessionExpiredException(sessionId);
        }

        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public void validateSessionForStation(String sessionId, Long stationId) {
        CustomerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.isExpired() || isExpiredByTime(session)) {
            throw new SessionExpiredException(sessionId);
        }

        Long sessionStationId = session.getStation().getId();

        if (!sessionStationId.equals(stationId)) {
            // Check if the session has non-terminal orders at its current station
            int activeOrdersAtCurrentStation = orderRepository.countBySessionSessionIdAndStateIn(
                    sessionId, ACTIVE_STATES);

            if (activeOrdersAtCurrentStation > 0) {
                throw new StationLockException(sessionStationId);
            }
            // If no active orders, the session is released — allow ordering at the new station
        }

        // Check concurrent order limit
        int activeOrderCount = orderRepository.countBySessionSessionIdAndStateIn(sessionId, ACTIVE_STATES);
        if (activeOrderCount >= MAX_CONCURRENT_ORDERS) {
            throw new MaxConcurrentOrdersException(activeOrderCount);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int getActiveOrderCount(String sessionId) {
        return orderRepository.countBySessionSessionIdAndStateIn(sessionId, ACTIVE_STATES);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionExpired(String sessionId) {
        CustomerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        return session.isExpired() || isExpiredByTime(session);
    }

    @Override
    @Transactional
    public void touchSession(String sessionId) {
        CustomerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void expireInactiveSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(SESSION_EXPIRY_HOURS);
        List<CustomerSession> inactiveSessions = sessionRepository
                .findByExpiredFalseAndLastActivityAtBefore(cutoff);

        for (CustomerSession session : inactiveSessions) {
            session.setExpired(true);
            sessionRepository.save(session);

            MDC.put("sessionId", session.getSessionId());
            log.info("Session expired: sessionId={}", session.getSessionId());

            // Cancel any DRAFT orders for this expired session
            List<CustomerOrder> draftOrders = orderRepository
                    .findBySessionSessionIdAndState(session.getSessionId(), OrderState.DRAFT);

            for (CustomerOrder order : draftOrders) {
                OrderState previousState = order.getState();
                order.setState(OrderState.CANCELLED);
                order.setUpdatedAt(LocalDateTime.now());

                OrderStateHistory history = new OrderStateHistory(
                        order, previousState, OrderState.CANCELLED, TransitionTrigger.SYSTEM);
                order.getStateHistory().add(history);

                orderRepository.save(order);
            }
        }
    }

    private boolean isExpiredByTime(CustomerSession session) {
        return session.getLastActivityAt()
                .plusHours(SESSION_EXPIRY_HOURS)
                .isBefore(LocalDateTime.now());
    }
}
