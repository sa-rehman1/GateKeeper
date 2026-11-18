package com.syed.gatekeeper.management.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
public class GatewayConfigurationKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigurationKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public GatewayConfigurationKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${gateway.kafka.configuration-topic}") String topic) {

        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(GatewayConfigurationChangeEvent event) {
        String key = event.getOrganizationId() + ":" +
                (event.getRouteId() == null ? "organization" : event.getRouteId());

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, message).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Failed to publish gateway configuration event {} (key {})",
                            event.getEventId(), key, error);
                } else {
                    log.info("Published gateway configuration event {} (key {}) to topic {}",
                            event.getEventId(), key, topic);
                }
            });
        } catch (Exception error) {
            log.error("Could not send gateway configuration event {} (key {})",
                    event.getEventId(), key, error);
        }
    }
}
