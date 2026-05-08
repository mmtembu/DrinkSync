package com.smarteventbar.dto;

import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderItemRequest {

    @NotNull
    private OrderItemType itemType;

    // For CUSTOM_DRINK: one or more spirit IDs and one or more mixer IDs
    private List<Long> spiritItemIds;
    private List<Long> mixerItemIds;

    // For PREMADE: single premade item ID
    private Long premadeItemId;

    private CupOption cupOption;

    @NotNull
    @Min(1)
    private Integer quantity;

    public OrderItemRequest() {
    }

    public OrderItemType getItemType() {
        return itemType;
    }

    public void setItemType(OrderItemType itemType) {
        this.itemType = itemType;
    }

    public List<Long> getSpiritItemIds() {
        return spiritItemIds;
    }

    public void setSpiritItemIds(List<Long> spiritItemIds) {
        this.spiritItemIds = spiritItemIds;
    }

    public List<Long> getMixerItemIds() {
        return mixerItemIds;
    }

    public void setMixerItemIds(List<Long> mixerItemIds) {
        this.mixerItemIds = mixerItemIds;
    }

    public Long getPremadeItemId() {
        return premadeItemId;
    }

    public void setPremadeItemId(Long premadeItemId) {
        this.premadeItemId = premadeItemId;
    }

    public CupOption getCupOption() {
        return cupOption;
    }

    public void setCupOption(CupOption cupOption) {
        this.cupOption = cupOption;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
