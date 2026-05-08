package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.service.VisualOrderNumberService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Property-based tests for VisualOrderNumberService.
 *
 * Property 8: Visual order number uniqueness
 * - Generate batches of orders transitioning to PAID; verify all visual order numbers are distinct
 * - Uniqueness is guaranteed by the combination of station prefix + monotonically increasing queue position
 *
 * Validates: Requirements 6.1
 */
class VisualOrderNumberServiceImplProperties {

    private final VisualOrderNumberService service = new VisualOrderNumberServiceImpl();

    // --- Property 8a: All visual order numbers within a single station batch are unique ---

    @Property
    void allVisualOrderNumbersAreUniqueWithinSameStation(
            @ForAll @IntRange(min = 1, max = 50) int batchSize) {

        Station station = new Station("Bar North", "Near entrance", BigDecimal.ONE, "ABC123", 10);
        station.setId(1L);

        Set<String> generatedNumbers = new HashSet<>();

        for (int i = 1; i <= batchSize; i++) {
            CustomerOrder order = new CustomerOrder(station, null);
            order.setQueuePosition(i);

            String visualOrderNumber = service.generateVisualOrderNumber(order);
            boolean isUnique = generatedNumbers.add(visualOrderNumber);

            assert isUnique :
                    "Visual order number '" + visualOrderNumber + "' was generated more than once. " +
                    "Duplicate found at queue position " + i + " in a batch of " + batchSize;
        }

        assert generatedNumbers.size() == batchSize :
                "Expected " + batchSize + " unique visual order numbers but got " + generatedNumbers.size();
    }

    // --- Property 8b: Visual order numbers are unique across different queue positions for any station name ---

    @Property
    void visualOrderNumbersAreUniqueForAnyStationName(
            @ForAll("stationNames") String stationName,
            @ForAll @IntRange(min = 1, max = 30) int batchSize) {

        Station station = new Station(stationName, "Location", BigDecimal.ONE, "XYZ789", 10);
        station.setId(1L);

        Set<String> generatedNumbers = new HashSet<>();

        for (int i = 1; i <= batchSize; i++) {
            CustomerOrder order = new CustomerOrder(station, null);
            order.setQueuePosition(i);

            String visualOrderNumber = service.generateVisualOrderNumber(order);
            boolean isUnique = generatedNumbers.add(visualOrderNumber);

            assert isUnique :
                    "Visual order number '" + visualOrderNumber + "' was duplicated for station '" +
                    stationName + "' at queue position " + i;
        }
    }

    // --- Property 8c: Visual order numbers are unique across multiple stations with distinct prefixes ---

    @Property
    void visualOrderNumbersAreUniqueAcrossStationsWithDistinctPrefixes(
            @ForAll @IntRange(min = 1, max = 20) int ordersPerStation) {

        Station stationA = new Station("Alpha Bar", "Section A", BigDecimal.ONE, "AAA111", 10);
        stationA.setId(1L);

        Station stationB = new Station("Beta Lounge", "Section B", BigDecimal.ONE, "BBB222", 10);
        stationB.setId(2L);

        Set<String> allNumbers = new HashSet<>();

        for (int i = 1; i <= ordersPerStation; i++) {
            CustomerOrder orderA = new CustomerOrder(stationA, null);
            orderA.setQueuePosition(i);
            String numberA = service.generateVisualOrderNumber(orderA);
            boolean uniqueA = allNumbers.add(numberA);

            assert uniqueA :
                    "Visual order number '" + numberA + "' from station 'Alpha Bar' collided " +
                    "with a previously generated number at queue position " + i;

            CustomerOrder orderB = new CustomerOrder(stationB, null);
            orderB.setQueuePosition(i);
            String numberB = service.generateVisualOrderNumber(orderB);
            boolean uniqueB = allNumbers.add(numberB);

            assert uniqueB :
                    "Visual order number '" + numberB + "' from station 'Beta Lounge' collided " +
                    "with a previously generated number at queue position " + i;
        }

        assert allNumbers.size() == ordersPerStation * 2 :
                "Expected " + (ordersPerStation * 2) + " unique numbers across 2 stations but got " +
                allNumbers.size();
    }

    // --- Property 8d: Same station prefix with same queue position produces same result (deterministic) ---
    // This ensures that uniqueness relies on distinct queue positions, not randomness.

    @Property
    void sameInputsProduceSameVisualOrderNumber(
            @ForAll("stationNames") String stationName,
            @ForAll @IntRange(min = 1, max = 999) int queuePosition) {

        Station station = new Station(stationName, "Location", BigDecimal.ONE, "DET456", 10);
        station.setId(1L);

        CustomerOrder order1 = new CustomerOrder(station, null);
        order1.setQueuePosition(queuePosition);
        String number1 = service.generateVisualOrderNumber(order1);

        CustomerOrder order2 = new CustomerOrder(station, null);
        order2.setQueuePosition(queuePosition);
        String number2 = service.generateVisualOrderNumber(order2);

        assert number1.equals(number2) :
                "Same station '" + stationName + "' and queue position " + queuePosition +
                " should produce the same visual order number, but got '" + number1 +
                "' and '" + number2 + "'";
    }

    // --- Property 8e: Different queue positions always produce different visual order numbers ---

    @Property
    void differentQueuePositionsProduceDifferentNumbers(
            @ForAll("stationNames") String stationName,
            @ForAll @IntRange(min = 1, max = 998) int position1) {

        int position2 = position1 + 1; // Guaranteed different

        Station station = new Station(stationName, "Location", BigDecimal.ONE, "DIF789", 10);
        station.setId(1L);

        CustomerOrder order1 = new CustomerOrder(station, null);
        order1.setQueuePosition(position1);
        String number1 = service.generateVisualOrderNumber(order1);

        CustomerOrder order2 = new CustomerOrder(station, null);
        order2.setQueuePosition(position2);
        String number2 = service.generateVisualOrderNumber(order2);

        assert !number1.equals(number2) :
                "Different queue positions (" + position1 + " and " + position2 +
                ") at station '" + stationName + "' must produce different visual order numbers, " +
                "but both produced '" + number1 + "'";
    }

    // --- Arbitrary providers ---

    @Provide
    Arbitrary<String> stationNames() {
        return Arbitraries.of(
                "Bar North",
                "Main Stage",
                "VIP Lounge",
                "Cocktails",
                "Beer Garden",
                "Drinks",
                "AB",
                "A",           // Falls back to "ST" prefix
                "",            // Falls back to "ST" prefix
                "123",         // Falls back to "ST" prefix (no letters)
                "X",           // Falls back to "ST" prefix (only 1 letter)
                "Bar 42",
                "south-west corner"
        );
    }
}
