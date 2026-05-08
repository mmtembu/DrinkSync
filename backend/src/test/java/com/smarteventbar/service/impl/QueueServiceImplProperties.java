package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.service.QueueService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for QueueService.
 *
 * Property 6: Queue position chronological ordering
 * - Generate random sequences of payment confirmations; verify monotonic queue positions
 * - Queue positions assigned to orders at the same station must be strictly increasing
 *
 * Validates: Requirements 4.4
 */
class QueueServiceImplProperties {

    // --- Property 6a: Queue positions are strictly monotonically increasing for a single station ---

    @Property
    void queuePositionsAreStrictlyIncreasingForSameStation(
            @ForAll @IntRange(min = 1, max = 20) int numberOfOrders) {

        OrderRepository orderRepository = mock(OrderRepository.class);

        // Simulate the repository returning the current max position.
        // Each call returns the previous max, which increments as orders are assigned.
        final int[] currentMax = {0};
        when(orderRepository.findMaxQueuePositionByStationId(anyLong()))
                .thenAnswer(invocation -> currentMax[0]);

        QueueService queueService = new QueueServiceImpl(orderRepository);

        Station station = new Station("Test Station", "Test Location", BigDecimal.ONE, "ABC123", 10);
        station.setId(1L);

        List<Integer> assignedPositions = new ArrayList<>();

        for (int i = 0; i < numberOfOrders; i++) {
            CustomerOrder order = new CustomerOrder();
            order.setStation(station);

            int position = queueService.assignQueuePosition(order);
            assignedPositions.add(position);

            // Update the simulated max to reflect the newly assigned position
            currentMax[0] = position;
        }

        // Verify strict monotonic increase
        for (int i = 1; i < assignedPositions.size(); i++) {
            assert assignedPositions.get(i) > assignedPositions.get(i - 1) :
                    "Queue positions must be strictly increasing. Position at index " + i +
                    " (" + assignedPositions.get(i) + ") is not greater than position at index " +
                    (i - 1) + " (" + assignedPositions.get(i - 1) + ")";
        }
    }

    // --- Property 6b: Queue positions start at 1 when no prior orders exist ---

    @Property
    void firstQueuePositionIsOneWhenNoPriorOrders(
            @ForAll @IntRange(min = 1, max = 10) int numberOfOrders) {

        OrderRepository orderRepository = mock(OrderRepository.class);

        // No prior orders — max position is 0
        final int[] currentMax = {0};
        when(orderRepository.findMaxQueuePositionByStationId(anyLong()))
                .thenAnswer(invocation -> currentMax[0]);

        QueueService queueService = new QueueServiceImpl(orderRepository);

        Station station = new Station("Fresh Station", "New Location", BigDecimal.ONE, "XYZ789", 10);
        station.setId(2L);

        CustomerOrder firstOrder = new CustomerOrder();
        firstOrder.setStation(station);

        int firstPosition = queueService.assignQueuePosition(firstOrder);
        currentMax[0] = firstPosition;

        assert firstPosition == 1 :
                "First queue position should be 1 when no prior orders exist, but was " + firstPosition;
    }

    // --- Property 6c: Queue positions continue from existing max ---

    @Property
    void queuePositionsContinueFromExistingMax(
            @ForAll @IntRange(min = 1, max = 100) int existingMax,
            @ForAll @IntRange(min = 1, max = 15) int numberOfNewOrders) {

        OrderRepository orderRepository = mock(OrderRepository.class);

        // Simulate existing orders already having positions up to existingMax
        final int[] currentMax = {existingMax};
        when(orderRepository.findMaxQueuePositionByStationId(anyLong()))
                .thenAnswer(invocation -> currentMax[0]);

        QueueService queueService = new QueueServiceImpl(orderRepository);

        Station station = new Station("Busy Station", "Busy Location", BigDecimal.ONE, "BUS456", 10);
        station.setId(3L);

        List<Integer> assignedPositions = new ArrayList<>();

        for (int i = 0; i < numberOfNewOrders; i++) {
            CustomerOrder order = new CustomerOrder();
            order.setStation(station);

            int position = queueService.assignQueuePosition(order);
            assignedPositions.add(position);
            currentMax[0] = position;
        }

        // First new position should be existingMax + 1
        assert assignedPositions.get(0) == existingMax + 1 :
                "First new queue position should be " + (existingMax + 1) +
                " (existingMax + 1) but was " + assignedPositions.get(0);

        // All positions should be strictly increasing
        for (int i = 1; i < assignedPositions.size(); i++) {
            assert assignedPositions.get(i) == assignedPositions.get(i - 1) + 1 :
                    "Queue positions must increment by exactly 1. Position at index " + i +
                    " (" + assignedPositions.get(i) + ") should be " +
                    (assignedPositions.get(i - 1) + 1);
        }
    }

    // --- Property 6d: Queue positions are independent across different stations ---

    @Property
    void queuePositionsAreIndependentAcrossStations(
            @ForAll @IntRange(min = 1, max = 10) int ordersPerStation) {

        OrderRepository orderRepository = mock(OrderRepository.class);

        // Track max per station independently
        final int[] stationAMax = {0};
        final int[] stationBMax = {0};

        when(orderRepository.findMaxQueuePositionByStationId(1L))
                .thenAnswer(invocation -> stationAMax[0]);
        when(orderRepository.findMaxQueuePositionByStationId(2L))
                .thenAnswer(invocation -> stationBMax[0]);

        QueueService queueService = new QueueServiceImpl(orderRepository);

        Station stationA = new Station("Station A", "Location A", BigDecimal.ONE, "AAA111", 10);
        stationA.setId(1L);

        Station stationB = new Station("Station B", "Location B", BigDecimal.ONE, "BBB222", 10);
        stationB.setId(2L);

        List<Integer> stationAPositions = new ArrayList<>();
        List<Integer> stationBPositions = new ArrayList<>();

        // Interleave orders between stations
        for (int i = 0; i < ordersPerStation; i++) {
            CustomerOrder orderA = new CustomerOrder();
            orderA.setStation(stationA);
            int posA = queueService.assignQueuePosition(orderA);
            stationAPositions.add(posA);
            stationAMax[0] = posA;

            CustomerOrder orderB = new CustomerOrder();
            orderB.setStation(stationB);
            int posB = queueService.assignQueuePosition(orderB);
            stationBPositions.add(posB);
            stationBMax[0] = posB;
        }

        // Both stations should have independent sequences starting at 1
        assert stationAPositions.get(0) == 1 :
                "Station A first position should be 1 but was " + stationAPositions.get(0);
        assert stationBPositions.get(0) == 1 :
                "Station B first position should be 1 but was " + stationBPositions.get(0);

        // Both should be strictly increasing independently
        for (int i = 1; i < ordersPerStation; i++) {
            assert stationAPositions.get(i) == stationAPositions.get(i - 1) + 1 :
                    "Station A positions must increment by 1";
            assert stationBPositions.get(i) == stationBPositions.get(i - 1) + 1 :
                    "Station B positions must increment by 1";
        }
    }

    // --- Property 6e: Assigned queue position is stored on the order object ---

    @Property
    void assignedPositionIsStoredOnOrderObject(
            @ForAll @IntRange(min = 1, max = 15) int numberOfOrders) {

        OrderRepository orderRepository = mock(OrderRepository.class);

        final int[] currentMax = {0};
        when(orderRepository.findMaxQueuePositionByStationId(anyLong()))
                .thenAnswer(invocation -> currentMax[0]);

        QueueService queueService = new QueueServiceImpl(orderRepository);

        Station station = new Station("Test Station", "Test Location", BigDecimal.ONE, "TST999", 10);
        station.setId(4L);

        for (int i = 0; i < numberOfOrders; i++) {
            CustomerOrder order = new CustomerOrder();
            order.setStation(station);

            int returnedPosition = queueService.assignQueuePosition(order);
            currentMax[0] = returnedPosition;

            assert order.getQueuePosition() != null :
                    "Order queue position should not be null after assignment";
            assert order.getQueuePosition() == returnedPosition :
                    "Order's stored queue position (" + order.getQueuePosition() +
                    ") should match returned position (" + returnedPosition + ")";
        }
    }
}
