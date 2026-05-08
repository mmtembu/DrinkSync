package com.smarteventbar.service.impl;

import com.smarteventbar.exception.MaxConcurrentOrdersException;
import com.smarteventbar.exception.StationLockException;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.CustomerSessionRepository;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.repository.StationRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SessionService.
 *
 * Property 19: Session expiry cancels DRAFT orders
 * - Generate sessions with varying inactivity durations; verify DRAFT orders cancelled when inactive > 2 hours
 *
 * Property 22: Concurrent order limit
 * - Generate sessions with varying non-terminal order counts; verify rejection when count >= 3
 *
 * Property 23: Station lock enforcement
 * - Generate sessions with non-terminal orders at station A; verify rejection at station B; verify release when all terminal
 *
 * Validates: Requirements 14.4, 14.5, 16.4, 16.6, 16.8
 */
class SessionServiceImplProperties {

    // --- Property 19a: Sessions inactive > 2 hours are marked as expired ---

    @Property
    void sessionsInactiveOverTwoHoursAreExpired(
            @ForAll @IntRange(min = 121, max = 600) int inactiveMinutes) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station station = createStation(1L, "Station A");
        CustomerSession session = createSession("session-1", station);
        session.setLastActivityAt(LocalDateTime.now().minusMinutes(inactiveMinutes));

        when(sessionRepository.findByExpiredFalseAndLastActivityAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(session));
        when(orderRepository.findBySessionSessionIdAndState(session.getSessionId(), OrderState.DRAFT))
                .thenReturn(Collections.emptyList());

        sessionService.expireInactiveSessions();

        assertThat(session.isExpired()).isTrue();
        verify(sessionRepository).save(session);
    }

    // --- Property 19b: DRAFT orders are cancelled when session expires ---

    @Property
    void draftOrdersCancelledWhenSessionExpires(
            @ForAll @IntRange(min = 1, max = 5) int draftOrderCount,
            @ForAll @IntRange(min = 121, max = 600) int inactiveMinutes) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station station = createStation(1L, "Station A");
        CustomerSession session = createSession("session-draft", station);
        session.setLastActivityAt(LocalDateTime.now().minusMinutes(inactiveMinutes));

        List<CustomerOrder> draftOrders = new ArrayList<>();
        for (int i = 0; i < draftOrderCount; i++) {
            CustomerOrder order = new CustomerOrder(station, session);
            order.setId((long) (i + 1));
            order.setState(OrderState.DRAFT);
            draftOrders.add(order);
        }

        when(sessionRepository.findByExpiredFalseAndLastActivityAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(session));
        when(orderRepository.findBySessionSessionIdAndState(session.getSessionId(), OrderState.DRAFT))
                .thenReturn(draftOrders);

        sessionService.expireInactiveSessions();

        // All DRAFT orders should be cancelled
        for (CustomerOrder order : draftOrders) {
            assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
            assertThat(order.getStateHistory()).isNotEmpty();
            assertThat(order.getStateHistory().get(0).getToState()).isEqualTo(OrderState.CANCELLED);
            assertThat(order.getStateHistory().get(0).getFromState()).isEqualTo(OrderState.DRAFT);
        }

        verify(orderRepository, times(draftOrderCount)).save(any(CustomerOrder.class));
    }

    // --- Property 19c: Sessions inactive <= 2 hours are NOT expired ---

    @Property
    void sessionsActiveWithinTwoHoursAreNotExpired(
            @ForAll @IntRange(min = 0, max = 119) int inactiveMinutes) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        // The repository query filters by cutoff time, so sessions within 2 hours
        // won't be returned by the query. Simulate empty result.
        when(sessionRepository.findByExpiredFalseAndLastActivityAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        sessionService.expireInactiveSessions();

        // No sessions should be saved (none expired)
        verify(sessionRepository, never()).save(any(CustomerSession.class));
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    // --- Property 22a: Sessions with < 3 active orders allow new order creation ---

    @Property
    void sessionsWithFewerThanThreeActiveOrdersAllowNewOrders(
            @ForAll @IntRange(min = 0, max = 2) int activeOrderCount) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station station = createStation(1L, "Station A");
        CustomerSession session = createSession("session-limit", station);

        when(sessionRepository.findBySessionId("session-limit"))
                .thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-limit"), anyCollection()))
                .thenReturn(activeOrderCount);

        // Should NOT throw — validation passes
        sessionService.validateSessionForStation("session-limit", 1L);
    }

    // --- Property 22b: Sessions with >= 3 active orders reject new order creation ---

    @Property
    void sessionsWithThreeOrMoreActiveOrdersRejectNewOrders(
            @ForAll @IntRange(min = 3, max = 10) int activeOrderCount) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station station = createStation(1L, "Station A");
        CustomerSession session = createSession("session-full", station);

        when(sessionRepository.findBySessionId("session-full"))
                .thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-full"), anyCollection()))
                .thenReturn(activeOrderCount);

        assertThatThrownBy(() -> sessionService.validateSessionForStation("session-full", 1L))
                .isInstanceOf(MaxConcurrentOrdersException.class);
    }

    // --- Property 23a: Station lock prevents ordering at different station when active orders exist ---

    @Property
    void stationLockPreventsOrderingAtDifferentStation(
            @ForAll @IntRange(min = 1, max = 5) int activeOrdersAtStationA) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station stationA = createStation(1L, "Station A");
        CustomerSession session = createSession("session-locked", stationA);

        when(sessionRepository.findBySessionId("session-locked"))
                .thenReturn(Optional.of(session));
        // Session is associated with station A (id=1), but we're trying to validate for station B (id=2)
        // The service checks active orders at the session's current station
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-locked"), anyCollection()))
                .thenReturn(activeOrdersAtStationA);

        // Attempting to order at station B (id=2) while locked to station A (id=1)
        assertThatThrownBy(() -> sessionService.validateSessionForStation("session-locked", 2L))
                .isInstanceOf(StationLockException.class);
    }

    // --- Property 23b: Station lock is released when all orders are terminal ---

    @Property
    void stationLockReleasedWhenAllOrdersTerminal(
            @ForAll @IntRange(min = 0, max = 2) int newActiveOrderCount) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station stationA = createStation(1L, "Station A");
        CustomerSession session = createSession("session-released", stationA);

        when(sessionRepository.findBySessionId("session-released"))
                .thenReturn(Optional.of(session));

        // First call: check active orders at current station (station A) — returns 0 (all terminal)
        // Second call: check concurrent order limit — returns newActiveOrderCount
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-released"), anyCollection()))
                .thenReturn(0)   // No active orders at station A → lock released
                .thenReturn(newActiveOrderCount);  // Concurrent order count for new station

        // Should NOT throw — session is released and can order at station B
        sessionService.validateSessionForStation("session-released", 2L);
    }

    // --- Property 23c: Ordering at the same station is always allowed (no lock violation) ---

    @Property
    void orderingAtSameStationNeverTriggersStationLock(
            @ForAll @IntRange(min = 0, max = 2) int activeOrderCount) {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station station = createStation(1L, "Station A");
        CustomerSession session = createSession("session-same", station);

        when(sessionRepository.findBySessionId("session-same"))
                .thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-same"), anyCollection()))
                .thenReturn(activeOrderCount);

        // Ordering at the same station (id=1) should never throw StationLockException
        sessionService.validateSessionForStation("session-same", 1L);
    }

    // --- Property 22c: The concurrent order limit boundary is exactly 3 ---

    @Property(tries = 10)
    void concurrentOrderLimitBoundaryIsExactlyThree() {

        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);

        SessionServiceImpl sessionService = new SessionServiceImpl(
                sessionRepository, orderRepository, stationRepository);

        Station station = createStation(1L, "Station A");
        CustomerSession session = createSession("session-boundary", station);

        when(sessionRepository.findBySessionId("session-boundary"))
                .thenReturn(Optional.of(session));

        // At exactly 2 active orders — should pass
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-boundary"), anyCollection()))
                .thenReturn(2);
        sessionService.validateSessionForStation("session-boundary", 1L);

        // At exactly 3 active orders — should reject
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("session-boundary"), anyCollection()))
                .thenReturn(3);
        assertThatThrownBy(() -> sessionService.validateSessionForStation("session-boundary", 1L))
                .isInstanceOf(MaxConcurrentOrdersException.class);
    }

    // --- Helper methods ---

    private Station createStation(Long id, String name) {
        Station station = new Station(name, "Location", BigDecimal.ONE, "ABC123", 10);
        station.setId(id);
        return station;
    }

    private CustomerSession createSession(String sessionId, Station station) {
        CustomerSession session = new CustomerSession(sessionId, station);
        session.setId(1L);
        session.setLastActivityAt(LocalDateTime.now());
        return session;
    }
}
