package com.smarteventbar.service.impl;

import com.smarteventbar.exception.MaxConcurrentOrdersException;
import com.smarteventbar.exception.SessionExpiredException;
import com.smarteventbar.exception.SessionNotFoundException;
import com.smarteventbar.exception.StationLockException;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.CustomerSessionRepository;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private CustomerSessionRepository sessionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StationRepository stationRepository;

    private SessionServiceImpl sessionService;

    private Station testStation;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(sessionRepository, orderRepository, stationRepository);
        testStation = new Station("Test Station", "Test Location", new BigDecimal("10.00"), "ABC123", 10);
        testStation.setId(1L);
    }

    // --- createSession tests ---

    @Test
    void createSession_validStation_createsAndReturnsSession() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));
        when(sessionRepository.save(any(CustomerSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerSession result = sessionService.createSession(1L);

        assertNotNull(result);
        assertNotNull(result.getSessionId());
        assertEquals(36, result.getSessionId().length()); // UUID format
        assertEquals(testStation, result.getStation());
        assertFalse(result.isExpired());
        assertNotNull(result.getLastActivityAt());
        verify(sessionRepository).save(any(CustomerSession.class));
    }

    @Test
    void createSession_invalidStation_throwsException() {
        when(stationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(999L));
    }

    // --- getSession tests ---

    @Test
    void getSession_validSession_returnsSession() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));

        CustomerSession result = sessionService.getSession("test-uuid");

        assertEquals(session, result);
    }

    @Test
    void getSession_notFound_throwsSessionNotFoundException() {
        when(sessionRepository.findBySessionId("nonexistent")).thenReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class, () -> sessionService.getSession("nonexistent"));
    }

    @Test
    void getSession_expiredSession_throwsSessionExpiredException() {
        CustomerSession session = new CustomerSession("expired-uuid", testStation);
        session.setExpired(true);
        when(sessionRepository.findBySessionId("expired-uuid")).thenReturn(Optional.of(session));

        assertThrows(SessionExpiredException.class, () -> sessionService.getSession("expired-uuid"));
    }

    // --- validateSessionForStation tests ---

    @Test
    void validateSessionForStation_sameStation_noActiveOrders_succeeds() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("test-uuid"), anyCollection())).thenReturn(0);

        assertDoesNotThrow(() -> sessionService.validateSessionForStation("test-uuid", 1L));
    }

    @Test
    void validateSessionForStation_sameStation_withActiveOrders_succeeds() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("test-uuid"), anyCollection())).thenReturn(2);

        assertDoesNotThrow(() -> sessionService.validateSessionForStation("test-uuid", 1L));
    }

    @Test
    void validateSessionForStation_differentStation_withActiveOrders_throwsStationLockException() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("test-uuid"), anyCollection())).thenReturn(2);

        StationLockException ex = assertThrows(StationLockException.class,
                () -> sessionService.validateSessionForStation("test-uuid", 2L));
        assertEquals(1L, ex.getLockedStationId());
    }

    @Test
    void validateSessionForStation_differentStation_noActiveOrders_succeeds() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));
        // First call: check active orders at current station (for station lock) → 0
        // Second call: check concurrent order limit → 0
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("test-uuid"), anyCollection())).thenReturn(0);

        assertDoesNotThrow(() -> sessionService.validateSessionForStation("test-uuid", 2L));
    }

    @Test
    void validateSessionForStation_maxConcurrentOrders_throwsMaxConcurrentOrdersException() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("test-uuid"), anyCollection())).thenReturn(3);

        assertThrows(MaxConcurrentOrdersException.class,
                () -> sessionService.validateSessionForStation("test-uuid", 1L));
    }

    @Test
    void validateSessionForStation_expiredSession_throwsSessionExpiredException() {
        CustomerSession session = new CustomerSession("expired-uuid", testStation);
        session.setExpired(true);
        when(sessionRepository.findBySessionId("expired-uuid")).thenReturn(Optional.of(session));

        assertThrows(SessionExpiredException.class,
                () -> sessionService.validateSessionForStation("expired-uuid", 1L));
    }

    @Test
    void validateSessionForStation_expiredByTime_throwsSessionExpiredException() {
        CustomerSession session = new CustomerSession("old-uuid", testStation);
        session.setLastActivityAt(LocalDateTime.now().minusHours(3));
        when(sessionRepository.findBySessionId("old-uuid")).thenReturn(Optional.of(session));

        assertThrows(SessionExpiredException.class,
                () -> sessionService.validateSessionForStation("old-uuid", 1L));
    }

    @Test
    void validateSessionForStation_notFound_throwsSessionNotFoundException() {
        when(sessionRepository.findBySessionId("nonexistent")).thenReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class,
                () -> sessionService.validateSessionForStation("nonexistent", 1L));
    }

    // --- getActiveOrderCount tests ---

    @Test
    void getActiveOrderCount_returnsCount() {
        when(orderRepository.countBySessionSessionIdAndStateIn(eq("test-uuid"), anyCollection())).thenReturn(2);

        assertEquals(2, sessionService.getActiveOrderCount("test-uuid"));
    }

    // --- isSessionExpired tests ---

    @Test
    void isSessionExpired_notExpired_returnsFalse() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));

        assertFalse(sessionService.isSessionExpired("test-uuid"));
    }

    @Test
    void isSessionExpired_markedExpired_returnsTrue() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        session.setExpired(true);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));

        assertTrue(sessionService.isSessionExpired("test-uuid"));
    }

    @Test
    void isSessionExpired_expiredByTime_returnsTrue() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        session.setLastActivityAt(LocalDateTime.now().minusHours(3));
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));

        assertTrue(sessionService.isSessionExpired("test-uuid"));
    }

    // --- touchSession tests ---

    @Test
    void touchSession_updatesLastActivityAt() {
        CustomerSession session = new CustomerSession("test-uuid", testStation);
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(30);
        session.setLastActivityAt(oldTime);
        when(sessionRepository.findBySessionId("test-uuid")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CustomerSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.touchSession("test-uuid");

        ArgumentCaptor<CustomerSession> captor = ArgumentCaptor.forClass(CustomerSession.class);
        verify(sessionRepository).save(captor.capture());
        assertTrue(captor.getValue().getLastActivityAt().isAfter(oldTime));
    }

    @Test
    void touchSession_notFound_throwsSessionNotFoundException() {
        when(sessionRepository.findBySessionId("nonexistent")).thenReturn(Optional.empty());

        assertThrows(SessionNotFoundException.class, () -> sessionService.touchSession("nonexistent"));
    }

    // --- expireInactiveSessions tests ---

    @Test
    void expireInactiveSessions_marksSessionsExpiredAndCancelsDraftOrders() {
        CustomerSession session = new CustomerSession("old-uuid", testStation);
        session.setLastActivityAt(LocalDateTime.now().minusHours(3));

        CustomerOrder draftOrder = new CustomerOrder(testStation, session);
        draftOrder.setId(1L);

        when(sessionRepository.findByExpiredFalseAndLastActivityAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(session));
        when(orderRepository.findBySessionSessionIdAndState("old-uuid", OrderState.DRAFT))
                .thenReturn(List.of(draftOrder));
        when(sessionRepository.save(any(CustomerSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.expireInactiveSessions();

        assertTrue(session.isExpired());
        assertEquals(OrderState.CANCELLED, draftOrder.getState());
        assertEquals(1, draftOrder.getStateHistory().size());
        assertEquals(OrderState.CANCELLED, draftOrder.getStateHistory().get(0).getToState());
    }

    @Test
    void expireInactiveSessions_noInactiveSessions_doesNothing() {
        when(sessionRepository.findByExpiredFalseAndLastActivityAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        sessionService.expireInactiveSessions();

        verify(orderRepository, never()).findBySessionSessionIdAndState(anyString(), any());
    }
}
