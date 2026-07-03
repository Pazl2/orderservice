package com.innowise.orderservice.messaging;

import com.innowise.orderservice.event.PaymentCompletedEvent;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;

    @KafkaListener(
            topics = "${spring.kafka.topic.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: orderId={}, status={}", event.orderId(), event.status());

        try {
            orderService.applyPaymentStatus(event.orderId(), event.status());
        } catch (ResourceNotFoundException ex) {
            log.error("Order {} not found, skipping PaymentCompletedEvent", event.orderId(), ex);
        }
    }
}