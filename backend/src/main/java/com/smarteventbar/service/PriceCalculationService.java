package com.smarteventbar.service;

import com.smarteventbar.model.entity.OrderItem;
import com.smarteventbar.model.enums.CupOption;

import java.math.BigDecimal;
import java.util.List;

public interface PriceCalculationService {

    /**
     * Calculates the price of a custom drink with multiple spirits and mixers.
     * Custom drink price = sum of all spirit prices + sum of all mixer prices + cup cost.
     * Cup cost is 0 for REUSE_CUP, or stationCupPrice for NEW_CUP.
     *
     * @param spiritPrices    the prices of all selected spirits
     * @param mixerPrices     the prices of all selected mixers
     * @param cupOption       the cup option chosen by the customer
     * @param stationCupPrice the station's configured new cup price
     * @return the total custom drink price
     */
    BigDecimal calculateCustomDrinkPrice(List<BigDecimal> spiritPrices, List<BigDecimal> mixerPrices, CupOption cupOption, BigDecimal stationCupPrice);

    /**
     * Calculates the total price of an order.
     * Order total = sum of (unitPrice × quantity) for all order items.
     * Returns BigDecimal.ZERO if the list is empty.
     *
     * @param items the list of order items
     * @return the total order price
     */
    BigDecimal calculateOrderTotal(List<OrderItem> items);
}
