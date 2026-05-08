package com.smarteventbar.service;

import com.smarteventbar.model.entity.CustomerOrder;

/**
 * Generates unique, human-readable visual order numbers for orders
 * when they transition to PAID state.
 *
 * The visual order number is designed to be easy for customers to show
 * vendors at pickup for order verification.
 */
public interface VisualOrderNumberService {

    /**
     * Generates a unique visual order number for the given order and sets it
     * on the order entity.
     *
     * The format is: {STATION_PREFIX}-{PADDED_NUMBER} where:
     * - STATION_PREFIX = first 2 uppercase letters of the station name (or "ST" if name is too short)
     * - PADDED_NUMBER = queue position zero-padded to 3 digits
     *
     * Example: Station "Bar North" with queue position 7 → "BA-007"
     *
     * @param order the order to generate a visual order number for; must have a station and queue position assigned
     * @return the generated visual order number
     */
    String generateVisualOrderNumber(CustomerOrder order);
}
