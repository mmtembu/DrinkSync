package com.smarteventbar.model.entity;

import com.smarteventbar.model.enums.OrderState;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id")
    private CustomerSession session;

    @Column(name = "visual_order_number", unique = true, length = 20)
    private String visualOrderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderState state;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "pickup_window_start")
    private LocalDateTime pickupWindowStart;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderStateHistory> stateHistory = new ArrayList<>();

    public CustomerOrder() {
    }

    public CustomerOrder(Station station, CustomerSession session) {
        this.station = station;
        this.session = session;
        this.state = OrderState.DRAFT;
        this.totalPrice = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public CustomerSession getSession() {
        return session;
    }

    public void setSession(CustomerSession session) {
        this.session = session;
    }

    public String getVisualOrderNumber() {
        return visualOrderNumber;
    }

    public void setVisualOrderNumber(String visualOrderNumber) {
        this.visualOrderNumber = visualOrderNumber;
    }

    public OrderState getState() {
        return state;
    }

    public void setState(OrderState state) {
        this.state = state;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getPickupWindowStart() {
        return pickupWindowStart;
    }

    public void setPickupWindowStart(LocalDateTime pickupWindowStart) {
        this.pickupWindowStart = pickupWindowStart;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public List<OrderStateHistory> getStateHistory() {
        return stateHistory;
    }

    public void setStateHistory(List<OrderStateHistory> stateHistory) {
        this.stateHistory = stateHistory;
    }
}
