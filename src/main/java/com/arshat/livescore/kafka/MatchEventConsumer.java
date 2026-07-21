package com.arshat.livescore.kafka;

import com.arshat.livescore.dto.MatchEventMessage;
import com.arshat.livescore.service.MatchEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchEventConsumer.class);

    private final MatchEventProcessor processor;

    public MatchEventConsumer(MatchEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${app.kafka.match-events-topic}")
    public void onMatchEvent(MatchEventMessage message) {
        log.info("Consumed event {} ({}) for match {}",
                message.eventId(), message.type(), message.matchId());
        processor.process(message);
    }
}
