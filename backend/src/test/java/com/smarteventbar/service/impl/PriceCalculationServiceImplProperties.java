package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.OrderItem;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.service.PriceCalculationService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Property-based tests for PriceCalculationService.
 *
 * Property 3: Custom drink price calculation
 * - Generate random spirit prices, mixer prices, cup prices; verify sum
 *
 * Property 4: Order total price calculation
 * - Generate random lists of order items with prices and quantities; verify total
 *
 * Validates: Requirements 3.4, 3.7
 */
class PriceCalculationServiceImplProperties {

    private final PriceCalculationService service = new PriceCalculationServiceImpl();

    // --- Arbitraries ---

    @Provide
    Arbitrary<BigDecimal> positivePrices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> nonNegativePrices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(9999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<CupOption> cupOptions() {
        return Arbitraries.of(CupOption.values());
    }

    @Provide
    Arbitrary<OrderItem> orderItems() {
        return Combinators.combine(
                Arbitraries.of(OrderItemType.values()),
                Arbitraries.integers().between(1, 20),
                positivePrices()
        ).as((itemType, quantity, unitPrice) -> new OrderItem(null, itemType, quantity, unitPrice));
    }

    @Provide
    Arbitrary<List<OrderItem>> orderItemLists() {
        return orderItems().list().ofMinSize(1).ofMaxSize(20);
    }

    // --- Property 3: Custom drink price calculation ---

    /**
     * Property 3a: Custom drink price with REUSE_CUP equals sum of spirits + sum of mixers.
     * When cup option is REUSE_CUP, the cup cost is zero regardless of station cup price.
     */
    @Property
    void customDrinkPriceWithReuseCupEqualsSpiritPlusMixer(
            @ForAll("positivePrices") BigDecimal spiritPrice,
            @ForAll("positivePrices") BigDecimal mixerPrice,
            @ForAll("nonNegativePrices") BigDecimal stationCupPrice) {

        BigDecimal result = service.calculateCustomDrinkPrice(
                List.of(spiritPrice), List.of(mixerPrice), CupOption.REUSE_CUP, stationCupPrice);

        BigDecimal expected = spiritPrice.add(mixerPrice);

        assert result.compareTo(expected) == 0 :
                "REUSE_CUP: expected " + expected + " but got " + result +
                " (spirit=" + spiritPrice + ", mixer=" + mixerPrice + ", cupPrice=" + stationCupPrice + ")";
    }

    /**
     * Property 3b: Custom drink price with NEW_CUP equals sum of spirits + sum of mixers + cup price.
     * When cup option is NEW_CUP, the station cup price is added.
     */
    @Property
    void customDrinkPriceWithNewCupEqualsSpiritPlusMixerPlusCupPrice(
            @ForAll("positivePrices") BigDecimal spiritPrice,
            @ForAll("positivePrices") BigDecimal mixerPrice,
            @ForAll("nonNegativePrices") BigDecimal stationCupPrice) {

        BigDecimal result = service.calculateCustomDrinkPrice(
                List.of(spiritPrice), List.of(mixerPrice), CupOption.NEW_CUP, stationCupPrice);

        BigDecimal expected = spiritPrice.add(mixerPrice).add(stationCupPrice);

        assert result.compareTo(expected) == 0 :
                "NEW_CUP: expected " + expected + " but got " + result +
                " (spirit=" + spiritPrice + ", mixer=" + mixerPrice + ", cupPrice=" + stationCupPrice + ")";
    }

    /**
     * Property 3c: Custom drink price is always >= sum of spirits + sum of mixers.
     * Regardless of cup option, the price is never less than the sum of all spirits and mixers.
     */
    @Property
    void customDrinkPriceIsAlwaysAtLeastSpiritPlusMixer(
            @ForAll("positivePrices") BigDecimal spiritPrice,
            @ForAll("positivePrices") BigDecimal mixerPrice,
            @ForAll("cupOptions") CupOption cupOption,
            @ForAll("nonNegativePrices") BigDecimal stationCupPrice) {

        BigDecimal result = service.calculateCustomDrinkPrice(
                List.of(spiritPrice), List.of(mixerPrice), cupOption, stationCupPrice);

        BigDecimal minimum = spiritPrice.add(mixerPrice);

        assert result.compareTo(minimum) >= 0 :
                "Custom drink price " + result + " is less than spirit + mixer = " + minimum;
    }

    /**
     * Property 3d: Custom drink price difference between NEW_CUP and REUSE_CUP equals station cup price.
     * The price difference between the two cup options is exactly the station cup price.
     */
    @Property
    void priceDifferenceBetweenCupOptionsEqualsStationCupPrice(
            @ForAll("positivePrices") BigDecimal spiritPrice,
            @ForAll("positivePrices") BigDecimal mixerPrice,
            @ForAll("nonNegativePrices") BigDecimal stationCupPrice) {

        BigDecimal newCupPrice = service.calculateCustomDrinkPrice(
                List.of(spiritPrice), List.of(mixerPrice), CupOption.NEW_CUP, stationCupPrice);
        BigDecimal reuseCupPrice = service.calculateCustomDrinkPrice(
                List.of(spiritPrice), List.of(mixerPrice), CupOption.REUSE_CUP, stationCupPrice);

        BigDecimal difference = newCupPrice.subtract(reuseCupPrice);

        assert difference.compareTo(stationCupPrice) == 0 :
                "Difference between NEW_CUP and REUSE_CUP should be " + stationCupPrice +
                " but was " + difference;
    }

    /**
     * Property 3e: Custom drink price is always positive when inputs are positive.
     */
    @Property
    void customDrinkPriceIsAlwaysPositive(
            @ForAll("positivePrices") BigDecimal spiritPrice,
            @ForAll("positivePrices") BigDecimal mixerPrice,
            @ForAll("cupOptions") CupOption cupOption,
            @ForAll("nonNegativePrices") BigDecimal stationCupPrice) {

        BigDecimal result = service.calculateCustomDrinkPrice(
                List.of(spiritPrice), List.of(mixerPrice), cupOption, stationCupPrice);

        assert result.compareTo(BigDecimal.ZERO) > 0 :
                "Custom drink price should be positive but was " + result;
    }

    /**
     * Property 3f: Multi-spirit multi-mixer price equals sum of all spirits + sum of all mixers + cup cost.
     * Tests the core multi-select property with multiple items.
     */
    @Property
    void multiSpiritMultiMixerPriceEqualsSum(
            @ForAll("positivePrices") BigDecimal spirit1,
            @ForAll("positivePrices") BigDecimal spirit2,
            @ForAll("positivePrices") BigDecimal mixer1,
            @ForAll("positivePrices") BigDecimal mixer2,
            @ForAll("cupOptions") CupOption cupOption,
            @ForAll("nonNegativePrices") BigDecimal stationCupPrice) {

        List<BigDecimal> spirits = List.of(spirit1, spirit2);
        List<BigDecimal> mixers = List.of(mixer1, mixer2);

        BigDecimal result = service.calculateCustomDrinkPrice(spirits, mixers, cupOption, stationCupPrice);

        BigDecimal cupCost = (cupOption == CupOption.NEW_CUP) ? stationCupPrice : BigDecimal.ZERO;
        BigDecimal expected = spirit1.add(spirit2).add(mixer1).add(mixer2).add(cupCost);

        assert result.compareTo(expected) == 0 :
                "Multi-select price expected " + expected + " but got " + result;
    }

    // --- Property 4: Order total price calculation ---

    /**
     * Property 4a: Order total equals sum of (unit_price × quantity) for all items.
     * This is the core correctness property for order total calculation.
     */
    @Property
    void orderTotalEqualsSumOfLineTotals(
            @ForAll("orderItemLists") List<OrderItem> items) {

        BigDecimal result = service.calculateOrderTotal(items);

        BigDecimal expected = BigDecimal.ZERO;
        for (OrderItem item : items) {
            expected = expected.add(
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        assert result.compareTo(expected) == 0 :
                "Order total expected " + expected + " but got " + result;
    }

    /**
     * Property 4b: Order total is always non-negative when items have positive prices and quantities.
     */
    @Property
    void orderTotalIsAlwaysNonNegative(
            @ForAll("orderItemLists") List<OrderItem> items) {

        BigDecimal result = service.calculateOrderTotal(items);

        assert result.compareTo(BigDecimal.ZERO) >= 0 :
                "Order total should be non-negative but was " + result;
    }

    /**
     * Property 4c: Order total is strictly positive for non-empty item lists with positive prices.
     */
    @Property
    void orderTotalIsPositiveForNonEmptyList(
            @ForAll("orderItemLists") List<OrderItem> items) {

        BigDecimal result = service.calculateOrderTotal(items);

        assert result.compareTo(BigDecimal.ZERO) > 0 :
                "Order total should be positive for non-empty list but was " + result;
    }

    /**
     * Property 4d: Adding an item to the order increases the total.
     * Order total with N+1 items >= order total with N items (monotonicity).
     */
    @Property
    void addingItemIncreasesOrMaintainsTotal(
            @ForAll("orderItemLists") List<OrderItem> items,
            @ForAll("orderItems") OrderItem additionalItem) {

        BigDecimal originalTotal = service.calculateOrderTotal(items);

        List<OrderItem> extendedItems = new ArrayList<>(items);
        extendedItems.add(additionalItem);
        BigDecimal extendedTotal = service.calculateOrderTotal(extendedItems);

        assert extendedTotal.compareTo(originalTotal) > 0 :
                "Adding an item should increase total. Original: " + originalTotal +
                ", Extended: " + extendedTotal;
    }

    /**
     * Property 4e: Order total is independent of item ordering (commutativity).
     * Shuffling items should not change the total.
     */
    @Property
    void orderTotalIsIndependentOfItemOrdering(
            @ForAll("orderItemLists") List<OrderItem> items) {

        BigDecimal originalTotal = service.calculateOrderTotal(items);

        // Reverse the list as a simple reordering
        List<OrderItem> reversed = new ArrayList<>(items);
        java.util.Collections.reverse(reversed);
        BigDecimal reversedTotal = service.calculateOrderTotal(reversed);

        assert originalTotal.compareTo(reversedTotal) == 0 :
                "Order total should be independent of item order. Original: " + originalTotal +
                ", Reversed: " + reversedTotal;
    }

    /**
     * Property 4f: Empty and null item lists return zero.
     */
    @Property(tries = 1)
    void emptyAndNullListsReturnZero() {
        BigDecimal emptyResult = service.calculateOrderTotal(List.of());
        BigDecimal nullResult = service.calculateOrderTotal(null);

        assert emptyResult.compareTo(BigDecimal.ZERO) == 0 :
                "Empty list should return zero but got " + emptyResult;
        assert nullResult.compareTo(BigDecimal.ZERO) == 0 :
                "Null list should return zero but got " + nullResult;
    }

    /**
     * Property 4g: Single item order total equals unit_price × quantity.
     */
    @Property
    void singleItemTotalEqualsUnitPriceTimesQuantity(
            @ForAll("orderItems") OrderItem item) {

        BigDecimal result = service.calculateOrderTotal(List.of(item));

        BigDecimal expected = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        assert result.compareTo(expected) == 0 :
                "Single item total expected " + expected + " but got " + result +
                " (unitPrice=" + item.getUnitPrice() + ", quantity=" + item.getQuantity() + ")";
    }
}
