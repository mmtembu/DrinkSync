package com.smarteventbar.model.entity;

import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.model.enums.TransitionTrigger;

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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_state_history")
public class OrderStateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    private OrderState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 30)
    private OrderState toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    private TransitionTrigger triggeredBy;

    @Column(name = "transitioned_at", nullable = false)
    private LocalDateTime transitionedAt;

    public OrderStateHistory() {
    }

    public OrderStateHistory(CustomerOrder order, OrderState fromState, OrderState toState, TransitionTrigger triggeredBy) {
        this.order = order;
        this.fromState = fromState;
        this.toState = toState;
        this.triggeredBy = triggeredBy;
        this.transitionedAt = LocalDateTime.now();
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

    public OrderState getFromState() {
        return fromState;
    }

    public void setFromState(OrderState fromState) {
        this.fromState = fromState;
    }

    public OrderState getToState() {
        return toState;
    }

    public void setToState(OrderState toState) {
        this.toState = toState;
    }

    public TransitionTrigger getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(TransitionTrigger triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public LocalDateTime getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(LocalDateTime transitionedAt) {
        this.transitionedAt = transitionedAt;
    }
}
