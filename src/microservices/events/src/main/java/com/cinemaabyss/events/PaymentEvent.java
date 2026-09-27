package com.cinemaabyss.events;

public record PaymentEvent(
        Long paymentId,
        Long userId,
        Double amount,
        String status,
        String timestamp,
        String methodType) {
}
