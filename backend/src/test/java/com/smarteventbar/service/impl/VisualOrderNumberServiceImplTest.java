package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.service.VisualOrderNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VisualOrderNumberServiceImplTest {

    private VisualOrderNumberService service;

    @BeforeEach
    void setUp() {
        service = new VisualOrderNumberServiceImpl();
    }

    @Test
    void generateVisualOrderNumber_typicalStation() {
        Station station = new Station("Bar North", "Near entrance", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(7);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("BA-007", result);
        assertEquals("BA-007", order.getVisualOrderNumber());
    }

    @Test
    void generateVisualOrderNumber_singleWordStation() {
        Station station = new Station("Drinks", "Main area", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(42);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("DR-042", result);
    }

    @Test
    void generateVisualOrderNumber_lowercaseStationName() {
        Station station = new Station("cocktails", "VIP area", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(1);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("CO-001", result);
    }

    @Test
    void generateVisualOrderNumber_stationNameWithNumbers() {
        Station station = new Station("Bar 42", "Section B", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(15);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("BA-015", result);
    }

    @Test
    void generateVisualOrderNumber_singleCharStationName_fallsBackToDefault() {
        Station station = new Station("A", "Corner", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(3);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("ST-003", result);
    }

    @Test
    void generateVisualOrderNumber_emptyStationName_fallsBackToDefault() {
        Station station = new Station("", "Nowhere", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(99);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("ST-099", result);
    }

    @Test
    void generateVisualOrderNumber_numericOnlyStationName_fallsBackToDefault() {
        Station station = new Station("123", "Lot 3", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(5);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("ST-005", result);
    }

    @Test
    void generateVisualOrderNumber_largeQueuePosition() {
        Station station = new Station("Main Bar", "Center", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(1234);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("MA-1234", result);
    }

    @Test
    void generateVisualOrderNumber_queuePositionOne() {
        Station station = new Station("VIP Lounge", "Upstairs", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(1);

        String result = service.generateVisualOrderNumber(order);

        assertEquals("VI-001", result);
    }

    @Test
    void generateVisualOrderNumber_setsValueOnOrder() {
        Station station = new Station("Test Bar", "Test", BigDecimal.ONE, "ABC123", 10);
        CustomerOrder order = new CustomerOrder(station, null);
        order.setQueuePosition(10);

        service.generateVisualOrderNumber(order);

        assertNotNull(order.getVisualOrderNumber());
        assertEquals("TE-010", order.getVisualOrderNumber());
    }
}
