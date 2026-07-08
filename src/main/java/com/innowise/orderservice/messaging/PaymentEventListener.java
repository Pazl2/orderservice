package com.innowise.orderservice.messaging;

import com.innowise.orderservice.event.PaymentCompletedEvent;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;
    private final MeterRegistry meterRegistry;

    private Counter dltCounter;

    @PostConstruct
    void initMetrics() {
        dltCounter = Counter.builder("order_service.payment_events.dlt")
                .description("PaymentCompletedEvents that exhausted retries and landed on the dead-letter topic")
                .register(meterRegistry);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
            include = ResourceNotFoundException.class,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = "${spring.kafka.topic.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: orderId={}, status={}", event.orderId(), event.status());
        orderService.applyPaymentStatus(event.orderId(), event.status());
    }

    @DltHandler
    public void onPaymentCompletedDlt(PaymentCompletedEvent event,
                                      ConsumerRecord<String, PaymentCompletedEvent> record,
                                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                      @Header(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS) int attempts) {
        dltCounter.increment();
    }
}