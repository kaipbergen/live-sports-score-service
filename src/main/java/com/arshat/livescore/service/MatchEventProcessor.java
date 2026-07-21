package com.arshat.livescore.service;

import com.arshat.livescore.domain.Event;
import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.Score;
import com.arshat.livescore.dto.MatchEventMessage;
import com.arshat.livescore.exception.NotFoundException;
import com.arshat.livescore.repository.EventRepository;
import com.arshat.livescore.repository.MatchRepository;
import com.arshat.livescore.repository.ScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side business logic: persists the event and applies its effect on
 * match state (score, status) in a single transaction.
 */
@Service
public class MatchEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(MatchEventProcessor.class);

    private final MatchRepository matchRepository;
    private final ScoreRepository scoreRepository;
    private final EventRepository eventRepository;

    public MatchEventProcessor(MatchRepository matchRepository,
                               ScoreRepository scoreRepository,
                               EventRepository eventRepository) {
        this.matchRepository = matchRepository;
        this.scoreRepository = scoreRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void process(MatchEventMessage message) {
        // Kafka is at-least-once: on redelivery the same eventId is skipped.
        if (eventRepository.existsByExternalId(message.eventId())) {
            log.info("Event {} already processed, skipping", message.eventId());
            return;
        }

        Match match = matchRepository.findById(message.matchId())
                .orElseThrow(() -> new NotFoundException("Match not found: " + message.matchId()));

        eventRepository.save(new Event(
                message.eventId(), match, message.type(),
                message.team(), message.player(), message.minute()));

        switch (message.type()) {
            case GOAL -> {
                Score score = scoreRepository.findWithLockByMatchId(match.getId())
                        .orElseThrow(() -> new NotFoundException(
                                "Score not found for match " + match.getId()));
                score.addGoal(message.team());
            }
            case MATCH_STARTED -> match.setStatus(MatchStatus.LIVE);
            case MATCH_FINISHED -> match.setStatus(MatchStatus.FINISHED);
            default -> {
                // cards and substitutions only need to be recorded
            }
        }
    }
}
