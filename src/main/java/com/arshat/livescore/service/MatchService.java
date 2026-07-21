package com.arshat.livescore.service;

import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.Score;
import com.arshat.livescore.dto.CreateEventRequest;
import com.arshat.livescore.dto.CreateMatchRequest;
import com.arshat.livescore.dto.EventResponse;
import com.arshat.livescore.dto.MatchEventMessage;
import com.arshat.livescore.dto.MatchResponse;
import com.arshat.livescore.dto.ScoreResponse;
import com.arshat.livescore.exception.NotFoundException;
import com.arshat.livescore.kafka.MatchEventProducer;
import com.arshat.livescore.repository.EventRepository;
import com.arshat.livescore.repository.MatchRepository;
import com.arshat.livescore.repository.ScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final ScoreRepository scoreRepository;
    private final EventRepository eventRepository;
    private final MatchEventProducer producer;

    public MatchService(MatchRepository matchRepository,
                        ScoreRepository scoreRepository,
                        EventRepository eventRepository,
                        MatchEventProducer producer) {
        this.matchRepository = matchRepository;
        this.scoreRepository = scoreRepository;
        this.eventRepository = eventRepository;
        this.producer = producer;
    }

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest request) {
        Match match = matchRepository.save(
                new Match(request.homeTeam(), request.awayTeam(), request.startTime()));
        scoreRepository.save(new Score(match));
        return MatchResponse.from(match);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatches() {
        return matchRepository.findAll().stream().map(MatchResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long matchId) {
        return MatchResponse.from(requireMatch(matchId));
    }

    @Transactional(readOnly = true)
    public ScoreResponse getScore(Long matchId) {
        Match match = requireMatch(matchId);
        Score score = scoreRepository.findByMatchId(matchId)
                .orElseThrow(() -> new NotFoundException("Score not found for match " + matchId));
        return ScoreResponse.from(match, score);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEvents(Long matchId) {
        requireMatch(matchId);
        return eventRepository.findByMatchIdOrderByCreatedAtAsc(matchId).stream()
                .map(EventResponse::from)
                .toList();
    }

    /**
     * Validates the request against current match state and publishes the event
     * to Kafka. The DB state is updated asynchronously by the consumer.
     */
    public UUID publishEvent(Long matchId, CreateEventRequest request) {
        Match match = requireMatch(matchId);
        if (match.getStatus() == MatchStatus.FINISHED) {
            throw new IllegalStateException("Match " + matchId + " is already finished");
        }
        if (request.type() == EventType.GOAL && request.team() == null) {
            throw new IllegalArgumentException("team (HOME/AWAY) is required for GOAL events");
        }
        MatchEventMessage message = new MatchEventMessage(
                UUID.randomUUID(),
                matchId,
                request.type(),
                request.team(),
                request.player(),
                request.minute(),
                Instant.now()
        );
        producer.send(message);
        return message.eventId();
    }

    private Match requireMatch(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found: " + matchId));
    }
}
