package com.arshat.livescore.service;

import com.arshat.livescore.domain.Event;
import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.Score;
import com.arshat.livescore.dto.CreateEventRequest;
import com.arshat.livescore.dto.CreateMatchRequest;
import com.arshat.livescore.dto.EventResponse;
import com.arshat.livescore.dto.MatchEventMessage;
import com.arshat.livescore.dto.MatchResponse;
import com.arshat.livescore.dto.PageResponse;
import com.arshat.livescore.dto.ScoreResponse;
import com.arshat.livescore.dto.UpdateMatchStatusRequest;
import com.arshat.livescore.exception.NotFoundException;
import com.arshat.livescore.kafka.MatchEventProducer;
import com.arshat.livescore.repository.EventRepository;
import com.arshat.livescore.repository.MatchRepository;
import com.arshat.livescore.repository.ScoreRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    public PageResponse<MatchResponse> getMatches(Pageable pageable, MatchStatus status) {
        Page<Match> matches = status != null
                ? matchRepository.findByStatus(status, pageable)
                : matchRepository.findAll(pageable);
        return PageResponse.from(matches.map(MatchResponse::from));
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long matchId) {
        return MatchResponse.from(requireMatch(matchId));
    }

    @Transactional
    public MatchResponse updateStatus(Long matchId, UpdateMatchStatusRequest request) {
        Match match = requireMatch(matchId);
        match.setStatus(request.status());
        return MatchResponse.from(match);
    }

    @Transactional
    public void deleteMatch(Long matchId) {
        Match match = requireMatch(matchId);
        eventRepository.deleteByMatchId(matchId);
        scoreRepository.deleteByMatchId(matchId);
        matchRepository.delete(match);
    }

    @Transactional(readOnly = true)
    public ScoreResponse getScore(Long matchId) {
        Match match = requireMatch(matchId);
        Score score = scoreRepository.findByMatchId(matchId)
                .orElseThrow(() -> new NotFoundException("Score not found for match " + matchId));
        return ScoreResponse.from(match, score);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEvents(Long matchId, EventType type, Pageable pageable) {
        requireMatch(matchId);
        Page<Event> events = type != null
                ? eventRepository.findByMatchIdAndTypeOrderByCreatedAtAsc(matchId, type, pageable)
                : eventRepository.findByMatchIdOrderByCreatedAtAsc(matchId, pageable);
        return PageResponse.from(events.map(EventResponse::from));
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
