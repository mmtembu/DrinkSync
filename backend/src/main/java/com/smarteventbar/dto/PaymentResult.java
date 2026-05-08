package com.smarteventbar.dto;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents the result of a payment processing attempt.
 * Used by PaymentService to return payment outcomes, including
 * cached results for idempotent replay.
 */
public class PaymentResult {

    private final boolean success;
    private final int statusCode;
    private final String message;
    private final Long orderId;
    private final BigDecimal amount;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    @SuppressWarnings("unused")
    private PaymentResult() {
        this(false, 0, null, null, null);
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public PaymentResult(
            @com.fasterxml.jackson.annotation.JsonProperty("success") boolean success,
            @com.fasterxml.jackson.annotation.JsonProperty("statusCode") int statusCode,
            @com.fasterxml.jackson.annotation.JsonProperty("message") String message,
            @com.fasterxml.jackson.annotation.JsonProperty("orderId") Long orderId,
            @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount) {
        this.success = success;
        this.statusCode = statusCode;
        this.message = message;
        this.orderId = orderId;
        this.amount = amount;
    }

    public static PaymentResult success(Long orderId, BigDecimal amount) {
        return new PaymentResult(true, 200, "Payment successful", orderId, amount);
    }

    public static PaymentResult failure(Long orderId, BigDecimal amount, String reason) {
        return new PaymentResult(false, 500, reason, orderId, amount);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentResult that = (PaymentResult) o;
        return success == that.success
                && statusCode == that.statusCode
                && Objects.equals(message, that.message)
                && Objects.equals(orderId, that.orderId)
                && (amount == null ? that.amount == null : amount.compareTo(that.amount) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, statusCode, message, orderId, amount);
    }

    @Override
    public String toString() {
        return "PaymentResult{" +
                "success=" + success +
                ", statusCode=" + statusCode +
                ", message='" + message + '\'' +
                ", orderId=" + orderId +
                ", amount=" + amount +
                '}';
    }
}
