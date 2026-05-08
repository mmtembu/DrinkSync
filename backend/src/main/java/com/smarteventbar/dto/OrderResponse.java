package com.smarteventbar.dto;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.OrderItem;
import com.smarteventbar.model.enums.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    private Long id;
    private Long stationId;
    private String stationName;
    private String visualOrderNumber;
    private OrderState state;
    private Integer queuePosition;
    private BigDecimal totalPrice;
    private LocalDateTime pickupWindowStart;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;

    public OrderResponse() {
    }

    public static OrderResponse fromEntity(CustomerOrder order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setStationId(order.getStation().getId());
        response.setStationName(order.getStation().getName());
        response.setVisualOrderNumber(order.getVisualOrderNumber());
        response.setState(order.getState());
        response.setQueuePosition(order.getQueuePosition());
        response.setTotalPrice(order.getTotalPrice());
        response.setPickupWindowStart(order.getPickupWindowStart());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        response.setItems(order.getOrderItems().stream()
                .map(OrderItemResponse::fromEntity)
                .toList());
        return response;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getVisualOrderNumber() { return visualOrderNumber; }
    public void setVisualOrderNumber(String visualOrderNumber) { this.visualOrderNumber = visualOrderNumber; }
    public OrderState getState() { return state; }
    public void setState(OrderState state) { this.state = state; }
    public Integer getQueuePosition() { return queuePosition; }
    public void setQueuePosition(Integer queuePosition) { this.queuePosition = queuePosition; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public LocalDateTime getPickupWindowStart() { return pickupWindowStart; }
    public void setPickupWindowStart(LocalDateTime pickupWindowStart) { this.pickupWindowStart = pickupWindowStart; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public static class OrderItemResponse {
        private Long id;
        private String itemType;
        private List<SpiritItemRef> spiritItems;
        private List<MixerItemRef> mixerItems;
        private Long premadeItemId;
        private String premadeItemName;
        private String cupOption;
        private BigDecimal cupPrice;
        private int quantity;
        private BigDecimal unitPrice;

        public static OrderItemResponse fromEntity(OrderItem item) {
            OrderItemResponse r = new OrderItemResponse();
            r.setId(item.getId());
            r.setItemType(item.getItemType().name());
            r.setQuantity(item.getQuantity());
            r.setUnitPrice(item.getUnitPrice());
            r.setCupOption(item.getCupOption() != null ? item.getCupOption().name() : null);
            r.setCupPrice(item.getCupPrice());
            if (item.getSpiritItems() != null && !item.getSpiritItems().isEmpty()) {
                r.setSpiritItems(item.getSpiritItems().stream()
                        .map(s -> new SpiritItemRef(s.getId(), s.getName()))
                        .toList());
            }
            if (item.getMixerItems() != null && !item.getMixerItems().isEmpty()) {
                r.setMixerItems(item.getMixerItems().stream()
                        .map(m -> new MixerItemRef(m.getId(), m.getName()))
                        .toList());
            }
            if (item.getPremadeItem() != null) {
                r.setPremadeItemId(item.getPremadeItem().getId());
                r.setPremadeItemName(item.getPremadeItem().getName());
            }
            return r;
        }

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        public List<SpiritItemRef> getSpiritItems() { return spiritItems; }
        public void setSpiritItems(List<SpiritItemRef> spiritItems) { this.spiritItems = spiritItems; }
        public List<MixerItemRef> getMixerItems() { return mixerItems; }
        public void setMixerItems(List<MixerItemRef> mixerItems) { this.mixerItems = mixerItems; }
        public Long getPremadeItemId() { return premadeItemId; }
        public void setPremadeItemId(Long premadeItemId) { this.premadeItemId = premadeItemId; }
        public String getPremadeItemName() { return premadeItemName; }
        public void setPremadeItemName(String premadeItemName) { this.premadeItemName = premadeItemName; }
        public String getCupOption() { return cupOption; }
        public void setCupOption(String cupOption) { this.cupOption = cupOption; }
        public BigDecimal getCupPrice() { return cupPrice; }
        public void setCupPrice(BigDecimal cupPrice) { this.cupPrice = cupPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    public record SpiritItemRef(Long id, String name) {}
    public record MixerItemRef(Long id, String name) {}
}
