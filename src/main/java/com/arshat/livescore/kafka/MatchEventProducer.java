package com.arshat.livescore.kafka;

import com.arshat.livescore.dto.MatchEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MatchEventProducer {

    private static final Logger log = LoggerFactory.getLogger(MatchEventProducer.class);

    private final KafkaTemplate<String, MatchEventMessage> kafkaTemplate;
    private final String topic;

    public MatchEventProducer(KafkaTemplate<String, MatchEventMessage> kafkaTemplate,
                              @Value("${app.kafka.match-events-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Publishes the event keyed by matchId: all events of one match land in the
     * same partition, so Kafka preserves their order for the consumer.
     */
    public void send(MatchEventMessage message) {
        kafkaTemplate.send(topic, message.matchId().toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} for match {}",
                                message.eventId(), message.matchId(), ex);
                    } else {
                        log.info("Published event {} for match {} to {}-{}@{}",
                                message.eventId(), message.matchId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
