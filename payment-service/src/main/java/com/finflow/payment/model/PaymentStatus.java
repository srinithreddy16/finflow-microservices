package com.finflow.payment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("Payment initiated, awaiting processing"),
    PROCESSING("Payment is being processed"),
    COMPLETED("Payment successfully settled"),
    FAILED("Payment failed during processing"),
    REFUNDED("Payment has been refunded to sender");

    private final String description;
}
