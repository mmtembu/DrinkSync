package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.OrderItem;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.service.PriceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceCalculationServiceImplTest {

    private PriceCalculationService service;

    @BeforeEach
    void setUp() {
        service = new PriceCalculationServiceImpl();
    }

    // --- calculateCustomDrinkPrice tests (multi-select spirits + mixers) ---

    @Test
    void calculateCustomDrinkPrice_singleSpiritSingleMixer_reuseCup() {
        List<BigDecimal> spirits = List.of(new BigDecimal("50.00"));
        List<BigDecimal> mixers = List.of(new BigDecimal("15.00"));
        BigDecimal stationCupPrice = new BigDecimal("10.00");

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, CupOption.REUSE_CUP, stationCupPrice);

        assertEquals(new BigDecimal("65.00"), result);
    }

    @Test
    void calculateCustomDrinkPrice_singleSpiritSingleMixer_newCup() {
        List<BigDecimal> spirits = List.of(new BigDecimal("50.00"));
        List<BigDecimal> mixers = List.of(new BigDecimal("15.00"));
        BigDecimal stationCupPrice = new BigDecimal("10.00");

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, CupOption.NEW_CUP, stationCupPrice);

        assertEquals(new BigDecimal("75.00"), result);
    }

    @Test
    void calculateCustomDrinkPrice_multipleSpiritsMultipleMixers_reuseCup() {
        // vodka (50) + gin (45) + lemonade (15) + tonic (12) + reuse cup (0) = 122
        List<BigDecimal> spirits = List.of(new BigDecimal("50.00"), new BigDecimal("45.00"));
        List<BigDecimal> mixers = List.of(new BigDecimal("15.00"), new BigDecimal("12.00"));
        BigDecimal stationCupPrice = new BigDecimal("10.00");

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, CupOption.REUSE_CUP, stationCupPrice);

        assertEquals(new BigDecimal("122.00"), result);
    }

    @Test
    void calculateCustomDrinkPrice_multipleSpiritsMultipleMixers_newCup() {
        // vodka (50) + gin (45) + lemonade (15) + tonic (12) + new cup (10) = 132
        List<BigDecimal> spirits = List.of(new BigDecimal("50.00"), new BigDecimal("45.00"));
        List<BigDecimal> mixers = List.of(new BigDecimal("15.00"), new BigDecimal("12.00"));
        BigDecimal stationCupPrice = new BigDecimal("10.00");

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, CupOption.NEW_CUP, stationCupPrice);

        assertEquals(new BigDecimal("132.00"), result);
    }

    @Test
    void calculateCustomDrinkPrice_threeSpiritsTwoMixers() {
        // 30 + 40 + 50 + 10 + 15 + new cup (5) = 150
        List<BigDecimal> spirits = List.of(new BigDecimal("30.00"), new BigDecimal("40.00"), new BigDecimal("50.00"));
        List<BigDecimal> mixers = List.of(new BigDecimal("10.00"), new BigDecimal("15.00"));
        BigDecimal stationCupPrice = new BigDecimal("5.00");

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, CupOption.NEW_CUP, stationCupPrice);

        assertEquals(new BigDecimal("150.00"), result);
    }

    @Test
    void calculateCustomDrinkPrice_withZeroCupPrice_newCupAddsNothing() {
        List<BigDecimal> spirits = List.of(new BigDecimal("30.00"));
        List<BigDecimal> mixers = List.of(new BigDecimal("10.00"));
        BigDecimal stationCupPrice = BigDecimal.ZERO;

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, CupOption.NEW_CUP, stationCupPrice);

        assertEquals(new BigDecimal("40.00"), result);
    }

    // --- calculateOrderTotal tests ---

    @Test
    void calculateOrderTotal_emptyList_returnsZero() {
        BigDecimal result = service.calculateOrderTotal(Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void calculateOrderTotal_nullList_returnsZero() {
        BigDecimal result = service.calculateOrderTotal(null);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void calculateOrderTotal_singleItem_returnsUnitPriceTimesQuantity() {
        OrderItem item = new OrderItem(null, OrderItemType.PREMADE, 3, new BigDecimal("25.00"));

        BigDecimal result = service.calculateOrderTotal(List.of(item));

        assertEquals(new BigDecimal("75.00"), result);
    }

    @Test
    void calculateOrderTotal_multipleItems_returnsSumOfLineTotals() {
        OrderItem item1 = new OrderItem(null, OrderItemType.CUSTOM_DRINK, 2, new BigDecimal("65.00"));
        OrderItem item2 = new OrderItem(null, OrderItemType.PREMADE, 1, new BigDecimal("30.00"));
        OrderItem item3 = new OrderItem(null, OrderItemType.PREMADE, 4, new BigDecimal("15.00"));

        BigDecimal result = service.calculateOrderTotal(List.of(item1, item2, item3));

        // (2 * 65) + (1 * 30) + (4 * 15) = 130 + 30 + 60 = 220
        assertEquals(new BigDecimal("220.00"), result);
    }

    @Test
    void calculateOrderTotal_quantityOfOne_returnsUnitPrice() {
        OrderItem item = new OrderItem(null, OrderItemType.CUSTOM_DRINK, 1, new BigDecimal("75.50"));

        BigDecimal result = service.calculateOrderTotal(List.of(item));

        assertEquals(new BigDecimal("75.50"), result);
    }
}
