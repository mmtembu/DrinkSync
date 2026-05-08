package com.smarteventbar.model.entity;

import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private OrderItemType itemType;

    // Multiple spirits for a custom drink (via join table order_item_spirit)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "order_item_spirit",
            joinColumns = @JoinColumn(name = "order_item_id"),
            inverseJoinColumns = @JoinColumn(name = "spirit_item_id")
    )
    private List<SpiritItem> spiritItems = new ArrayList<>();

    // Multiple mixers for a custom drink (via join table order_item_mixer)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "order_item_mixer",
            joinColumns = @JoinColumn(name = "order_item_id"),
            inverseJoinColumns = @JoinColumn(name = "mixer_item_id")
    )
    private List<MixerItem> mixerItems = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "premade_item_id")
    private PremadeItem premadeItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "cup_option", length = 20)
    private CupOption cupOption;

    @Column(name = "cup_price", precision = 10, scale = 2)
    private BigDecimal cupPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderItem() {
    }

    public OrderItem(CustomerOrder order, OrderItemType itemType, int quantity, BigDecimal unitPrice) {
        this.order = order;
        this.itemType = itemType;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public OrderItemType getItemType() {
        return itemType;
    }

    public void setItemType(OrderItemType itemType) {
        this.itemType = itemType;
    }

    public List<SpiritItem> getSpiritItems() {
        return spiritItems;
    }

    public void setSpiritItems(List<SpiritItem> spiritItems) {
        this.spiritItems = spiritItems;
    }

    public List<MixerItem> getMixerItems() {
        return mixerItems;
    }

    public void setMixerItems(List<MixerItem> mixerItems) {
        this.mixerItems = mixerItems;
    }

    public PremadeItem getPremadeItem() {
        return premadeItem;
    }

    public void setPremadeItem(PremadeItem premadeItem) {
        this.premadeItem = premadeItem;
    }

    public CupOption getCupOption() {
        return cupOption;
    }

    public void setCupOption(CupOption cupOption) {
        this.cupOption = cupOption;
    }

    public BigDecimal getCupPrice() {
        return cupPrice;
    }

    public void setCupPrice(BigDecimal cupPrice) {
        this.cupPrice = cupPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
