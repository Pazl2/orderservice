package com.innowise.orderservice.integration;

import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.event.PaymentCompletedEvent;
import com.innowise.orderservice.integration.config.BaseIntegrationTest;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class PaymentEventListenerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
    }

    private Order persistOrder(OrderStatus initialStatus) {
        Order order = new Order();
        order.setUserId(1L);
        order.setStatus(initialStatus);
        order.setTotalPrice(new BigDecimal("100.00"));
        return orderRepository.save(order);
    }

    @Test
    void onPaymentCompleted_success_updatesOrderStatusToPaid() {
        Order order = persistOrder(OrderStatus.CREATED);

        kafkaTemplate.send("payment-events", String.valueOf(order.getId()),
                new PaymentCompletedEvent(order.getId(), "SUCCESS"));
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(order.getId()).orElseThrow();
                    assertEquals(OrderStatus.PAID, updated.getStatus());
                });
    }

    @Test
    void onPaymentCompleted_failed_updatesOrderStatusToCancelled() {
        Order order = persistOrder(OrderStatus.CREATED);

        kafkaTemplate.send("payment-events", String.valueOf(order.getId()),
                new PaymentCompletedEvent(order.getId(), "FAILED"));

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(order.getId()).orElseThrow();
                    assertEquals(OrderStatus.CANCELLED, updated.getStatus());
                });
    }

    @Test
    void onPaymentCompleted_unknownOrderId_doesNotThrowOrCrashListener() {
        kafkaTemplate.send("payment-events", "999999",
                new PaymentCompletedEvent(999999L, "SUCCESS"));

        Order order = persistOrder(OrderStatus.CREATED);
        kafkaTemplate.send("payment-events", String.valueOf(order.getId()),
                new PaymentCompletedEvent(order.getId(), "SUCCESS"));

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(order.getId()).orElseThrow();
                    assertSame(OrderStatus.PAID, updated.getStatus());
                });
    }
}