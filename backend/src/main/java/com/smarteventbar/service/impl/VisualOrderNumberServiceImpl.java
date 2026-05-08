package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.service.VisualOrderNumberService;
import org.springframework.stereotype.Service;

/**
 * Generates unique, human-readable visual order numbers.
 *
 * Format: {STATION_PREFIX}-{PADDED_NUMBER}
 * - STATION_PREFIX: first 2 uppercase letters of the station name (or "ST" if name is too short)
 * - PADDED_NUMBER: queue position zero-padded to 3 digits
 *
 * Example: Station "Bar North" with queue position 7 → "BA-007"
 *
 * Uniqueness is guaranteed because queue positions are monotonically increasing per station,
 * and the station prefix is derived from the station name. The visual_order_number column
 * also has a UNIQUE constraint in the database as a safety net.
 */
@Service
public class VisualOrderNumberServiceImpl implements VisualOrderNumberService {

    private static final String DEFAULT_PREFIX = "ST";
    private static final int PREFIX_LENGTH = 2;

    @Override
    public String generateVisualOrderNumber(CustomerOrder order) {
        String prefix = deriveStationPrefix(order.getStation());
        int queuePosition = order.getQueuePosition();
        String paddedNumber = String.format("%03d", queuePosition);

        String visualOrderNumber = prefix + "-" + paddedNumber;
        order.setVisualOrderNumber(visualOrderNumber);

        return visualOrderNumber;
    }

    /**
     * Derives a 2-character uppercase prefix from the station name.
     * Only alphabetic characters are considered. If the station name has fewer
     * than 2 alphabetic characters, falls back to "ST".
     *
     * @param station the station to derive the prefix from
     * @return a 2-character uppercase prefix
     */
    private String deriveStationPrefix(Station station) {
        if (station == null || station.getName() == null || station.getName().isBlank()) {
            return DEFAULT_PREFIX;
        }

        String letters = station.getName().chars()
                .filter(Character::isLetter)
                .limit(PREFIX_LENGTH)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .toUpperCase();

        if (letters.length() < PREFIX_LENGTH) {
            return DEFAULT_PREFIX;
        }

        return letters;
    }
}
