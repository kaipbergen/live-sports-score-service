package com.arshat.livescore.service;

import com.arshat.livescore.domain.Event;
import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.Score;
import com.arshat.livescore.domain.TeamSide;
import com.arshat.livescore.dto.MatchEventMessage;
import com.arshat.livescore.exception.NotFoundException;
import com.arshat.livescore.repository.EventRepository;
import com.arshat.livescore.repository.MatchRepository;
import com.arshat.livescore.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchEventProcessorTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private EventRepository eventRepository;

    private MatchEventProcessor processor;

    private Match match;
    private Score score;

    @BeforeEach
    void setUp() {
        processor = new MatchEventProcessor(matchRepository, scoreRepository, eventRepository);
        match = new Match("Zenit", "Spartak", Instant.now());
        score = new Score(match);
    }

    @Test
    void goalIncrementsHomeScore() {
        MatchEventMessage message = goal(TeamSide.HOME);
        when(eventRepository.existsByExternalId(message.eventId())).thenReturn(false);
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(scoreRepository.findWithLockByMatchId(any())).thenReturn(Optional.of(score));

        processor.process(message);

        assertThat(score.getHomeGoals()).isEqualTo(1);
        assertThat(score.getAwayGoals()).isZero();
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void goalIncrementsAwayScore() {
        MatchEventMessage message = goal(TeamSide.AWAY);
        when(eventRepository.existsByExternalId(message.eventId())).thenReturn(false);
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(scoreRepository.findWithLockByMatchId(any())).thenReturn(Optional.of(score));

        processor.process(message);

        assertThat(score.getAwayGoals()).isEqualTo(1);
    }

    @Test
    void duplicateEventIsSkipped() {
        MatchEventMessage message = goal(TeamSide.HOME);
        when(eventRepository.existsByExternalId(message.eventId())).thenReturn(true);

        processor.process(message);

        verify(eventRepository, never()).save(any());
        assertThat(score.getHomeGoals()).isZero();
    }

    @Test
    void matchStartedSwitchesStatusToLive() {
        MatchEventMessage message = new MatchEventMessage(
                UUID.randomUUID(), 1L, EventType.MATCH_STARTED, null, null, 1, Instant.now());
        when(eventRepository.existsByExternalId(message.eventId())).thenReturn(false);
        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        processor.process(message);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.LIVE);
    }

    @Test
    void unknownMatchThrowsNotFound() {
        MatchEventMessage message = goal(TeamSide.HOME);
        when(eventRepository.existsByExternalId(message.eventId())).thenReturn(false);
        when(matchRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(message))
                .isInstanceOf(NotFoundException.class);
    }

    private MatchEventMessage goal(TeamSide side) {
        return new MatchEventMessage(
                UUID.randomUUID(), 1L, EventType.GOAL, side, "Player", 10, Instant.now());
    }
}
