package com.smarteventbar.service;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for pickup window expiry.
 *
 * Property 20: Pickup window expiry transitions READY to EXPIRED
 * - Generate READY orders with varying pickup window start times and durations;
 *   verify transition to EXPIRED when window elapsed
 *
 * Validates: Requirements 15.1
 */
class PickupWindowExpiryProperties {

    // --- Property 20a: READY orders past their pickup window are expired ---

    @Property
    void readyOrdersPastPickupWindowAreExpired(
            @ForAll @IntRange(min = 5, max = 30) int pickupWindowMinutes,
            @ForAll @IntRange(min = 1, max = 120) int extraMinutesPastWindow) {

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        SessionService sessionService = mock(SessionService.class);

        ScheduledTaskService scheduledTaskService = new ScheduledTaskService(
                orderRepository, orderService, sessionService);

        Station station = createStation(1L, "Station A", pickupWindowMinutes);
        CustomerOrder order = createReadyOrder(1L, station);
        // Set pickup window start far enough in the past that it has elapsed
        order.setPickupWindowStart(
                LocalDateTime.now().minusMinutes(pickupWindowMinutes + extraMinutesPastWindow));

        when(orderRepository.findByState(OrderState.READY)).thenReturn(List.of(order));

        scheduledTaskService.expireReadyOrders();

        verify(orderService).expireOrder(order.getId());
    }

    // --- Property 20b: READY orders within their pickup window are NOT expired ---

    @Property
    void readyOrdersWithinPickupWindowAreNotExpired(
            @ForAll @IntRange(min = 5, max = 30) int pickupWindowMinutes,
            @ForAll @IntRange(min = 1, max = 29) int minutesBeforeExpiry) {

        // Ensure the order is still within the window
        int minutesSinceStart = Math.max(0, pickupWindowMinutes - minutesBeforeExpiry - 1);

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        SessionService sessionService = mock(SessionService.class);

        ScheduledTaskService scheduledTaskService = new ScheduledTaskService(
                orderRepository, orderService, sessionService);

        Station station = createStation(1L, "Station A", pickupWindowMinutes);
        CustomerOrder order = createReadyOrder(1L, station);
        // Set pickup window start so that the window has NOT yet elapsed
        order.setPickupWindowStart(LocalDateTime.now().minusMinutes(minutesSinceStart));

        when(orderRepository.findByState(OrderState.READY)).thenReturn(List.of(order));

        scheduledTaskService.expireReadyOrders();

        verify(orderService, never()).expireOrder(anyLong());
    }

    // --- Property 20c: Only READY orders are queried for expiry (not other states) ---

    @Property
    void onlyReadyOrdersAreQueriedForExpiry(
            @ForAll @IntRange(min = 5, max = 30) int pickupWindowMinutes) {

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        SessionService sessionService = mock(SessionService.class);

        ScheduledTaskService scheduledTaskService = new ScheduledTaskService(
                orderRepository, orderService, sessionService);

        when(orderRepository.findByState(OrderState.READY)).thenReturn(List.of());

        scheduledTaskService.expireReadyOrders();

        // Verify only READY state is queried
        verify(orderRepository).findByState(OrderState.READY);
        // Verify no other states are queried
        for (OrderState state : OrderState.values()) {
            if (state != OrderState.READY) {
                verify(orderRepository, never()).findByState(state);
            }
        }
        verify(orderService, never()).expireOrder(anyLong());
    }

    // --- Property 20d: READY orders with null pickupWindowStart are skipped ---

    @Property
    void readyOrdersWithNullPickupWindowStartAreSkipped(
            @ForAll @IntRange(min = 5, max = 30) int pickupWindowMinutes) {

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        SessionService sessionService = mock(SessionService.class);

        ScheduledTaskService scheduledTaskService = new ScheduledTaskService(
                orderRepository, orderService, sessionService);

        Station station = createStation(1L, "Station A", pickupWindowMinutes);
        CustomerOrder order = createReadyOrder(1L, station);
        order.setPickupWindowStart(null); // No pickup window start set

        when(orderRepository.findByState(OrderState.READY)).thenReturn(List.of(order));

        scheduledTaskService.expireReadyOrders();

        verify(orderService, never()).expireOrder(anyLong());
    }

    // --- Property 20e: Mixed batch — only elapsed orders are expired ---

    @Property
    void mixedBatchOnlyElapsedOrdersAreExpired(
            @ForAll @IntRange(min = 5, max = 30) int pickupWindowMinutes,
            @ForAll @IntRange(min = 1, max = 5) int expiredCount,
            @ForAll @IntRange(min = 1, max = 5) int activeCount) {

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        SessionService sessionService = mock(SessionService.class);

        ScheduledTaskService scheduledTaskService = new ScheduledTaskService(
                orderRepository, orderService, sessionService);

        Station station = createStation(1L, "Station A", pickupWindowMinutes);

        List<CustomerOrder> allOrders = new ArrayList<>();
        List<Long> expectedExpiredIds = new ArrayList<>();

        // Create orders that should be expired (pickup window elapsed)
        for (int i = 0; i < expiredCount; i++) {
            long orderId = i + 1;
            CustomerOrder order = createReadyOrder(orderId, station);
            order.setPickupWindowStart(
                    LocalDateTime.now().minusMinutes(pickupWindowMinutes + 5 + i));
            allOrders.add(order);
            expectedExpiredIds.add(orderId);
        }

        // Create orders that should NOT be expired (pickup window still active)
        for (int i = 0; i < activeCount; i++) {
            long orderId = expiredCount + i + 1;
            CustomerOrder order = createReadyOrder(orderId, station);
            order.setPickupWindowStart(LocalDateTime.now().minusMinutes(1));
            allOrders.add(order);
        }

        when(orderRepository.findByState(OrderState.READY)).thenReturn(allOrders);

        scheduledTaskService.expireReadyOrders();

        // Verify only the expired orders had expireOrder called
        for (Long expiredId : expectedExpiredIds) {
            verify(orderService).expireOrder(expiredId);
        }

        // Verify active orders were NOT expired
        for (int i = 0; i < activeCount; i++) {
            long activeId = expiredCount + i + 1;
            verify(orderService, never()).expireOrder(activeId);
        }
    }

    // --- Property 20f: Pickup window respects station-configured duration ---

    @Property
    void pickupWindowRespectsStationConfiguredDuration(
            @ForAll @IntRange(min = 5, max = 30) int pickupWindowMinutes) {

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService orderService = mock(OrderService.class);
        SessionService sessionService = mock(SessionService.class);

        ScheduledTaskService scheduledTaskService = new ScheduledTaskService(
                orderRepository, orderService, sessionService);

        Station station = createStation(1L, "Station A", pickupWindowMinutes);

        // Order that is clearly within the window (started 1 minute ago) — should NOT be expired
        CustomerOrder withinWindowOrder = createReadyOrder(1L, station);
        withinWindowOrder.setPickupWindowStart(LocalDateTime.now().minusMinutes(1));

        // Order that is clearly past the boundary — should be expired
        CustomerOrder pastOrder = createReadyOrder(2L, station);
        pastOrder.setPickupWindowStart(
                LocalDateTime.now().minusMinutes(pickupWindowMinutes + 2));

        // Order that is well before the boundary — should NOT be expired
        CustomerOrder beforeOrder = createReadyOrder(3L, station);
        beforeOrder.setPickupWindowStart(
                LocalDateTime.now().minusMinutes(Math.max(0, pickupWindowMinutes - 2)));

        when(orderRepository.findByState(OrderState.READY))
                .thenReturn(List.of(withinWindowOrder, pastOrder, beforeOrder));

        scheduledTaskService.expireReadyOrders();

        // Only the past-boundary order should be expired
        verify(orderService).expireOrder(2L);
        verify(orderService, never()).expireOrder(1L);
        verify(orderService, never()).expireOrder(3L);
    }

    // --- Helper methods ---

    private Station createStation(Long id, String name, int pickupWindowMinutes) {
        Station station = new Station(name, "Location", BigDecimal.ONE, "ABC123", pickupWindowMinutes);
        station.setId(id);
        return station;
    }

    private CustomerOrder createReadyOrder(Long id, Station station) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setStation(station);
        order.setState(OrderState.READY);
        order.setTotalPrice(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
