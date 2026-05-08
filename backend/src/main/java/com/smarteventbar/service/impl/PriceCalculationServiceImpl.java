package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.OrderItem;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.service.PriceCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PriceCalculationServiceImpl implements PriceCalculationService {

    @Override
    public BigDecimal calculateCustomDrinkPrice(List<BigDecimal> spiritPrices, List<BigDecimal> mixerPrices, CupOption cupOption, BigDecimal stationCupPrice) {
        BigDecimal spiritTotal = BigDecimal.ZERO;
        if (spiritPrices != null) {
            for (BigDecimal price : spiritPrices) {
                spiritTotal = spiritTotal.add(price);
            }
        }

        BigDecimal mixerTotal = BigDecimal.ZERO;
        if (mixerPrices != null) {
            for (BigDecimal price : mixerPrices) {
                mixerTotal = mixerTotal.add(price);
            }
        }

        BigDecimal cupCost = (cupOption == CupOption.NEW_CUP) ? stationCupPrice : BigDecimal.ZERO;
        return spiritTotal.add(mixerTotal).add(cupCost);
    }

    @Override
    public BigDecimal calculateOrderTotal(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(lineTotal);
        }
        return total;
    }
}
