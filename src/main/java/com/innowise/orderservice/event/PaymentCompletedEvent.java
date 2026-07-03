package com.innowise.orderservice.event;

import java.io.Serializable;

public record PaymentCompletedEvent(
        Long orderId,
        String status
) implements Serializable {
}