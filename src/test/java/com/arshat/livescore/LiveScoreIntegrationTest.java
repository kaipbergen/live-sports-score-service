package com.arshat.livescore;

import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.TeamSide;
import com.arshat.livescore.dto.CreateEventRequest;
import com.arshat.livescore.dto.CreateMatchRequest;
import com.arshat.livescore.dto.EventAcceptedResponse;
import com.arshat.livescore.dto.EventResponse;
import com.arshat.livescore.dto.MatchResponse;
import com.arshat.livescore.dto.PageResponse;
import com.arshat.livescore.dto.ScoreResponse;
import com.arshat.livescore.dto.UpdateMatchStatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end flow against real Postgres and Kafka started by Testcontainers:
 * REST -> producer -> Kafka -> consumer -> DB -> REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LiveScoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.1");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fullMatchFlow() {
        // 1. Create a match
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Zenit", "CSKA", Instant.now()),
                MatchResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long matchId = created.getBody().id();

        // 2. Initial score is 0:0, SCHEDULED
        ScoreResponse initial = rest.getForObject("/matches/{id}/score", ScoreResponse.class, matchId);
        assertThat(initial.homeGoals()).isZero();
        assertThat(initial.awayGoals()).isZero();
        assertThat(initial.status()).isEqualTo(MatchStatus.SCHEDULED);

        // 3. Publish MATCH_STARTED and a HOME goal through the API -> Kafka
        publish(matchId, new CreateEventRequest(EventType.MATCH_STARTED, null, null, 0));
        publish(matchId, new CreateEventRequest(EventType.GOAL, TeamSide.HOME, "Ivanov", 12));

        // 4. Consumer eventually updates the DB
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ScoreResponse score = rest.getForObject("/matches/{id}/score", ScoreResponse.class, matchId);
            assertThat(score.homeGoals()).isEqualTo(1);
            assertThat(score.awayGoals()).isZero();
            assertThat(score.status()).isEqualTo(MatchStatus.LIVE);
        });

        // 5. Events are recorded
        PageResponse<EventResponse> events = getEvents(matchId);
        assertThat(events.content()).hasSize(2);
        assertThat(events.content())
                .extracting(EventResponse::type)
                .containsExactly(EventType.MATCH_STARTED, EventType.GOAL);
    }

    @Test
    void adminCanOverrideMatchStatusDirectly() {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Dinamo", "Rubin", Instant.now()),
                MatchResponse.class);
        Long matchId = created.getBody().id();

        MatchResponse updated = rest.patchForObject(
                "/matches/{id}", new UpdateMatchStatusRequest(MatchStatus.FINISHED),
                MatchResponse.class, matchId);

        assertThat(updated.status()).isEqualTo(MatchStatus.FINISHED);

        MatchResponse fetched = rest.getForObject("/matches/{id}", MatchResponse.class, matchId);
        assertThat(fetched.status()).isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    void deletingMatchRemovesItsEventsAndScore() {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Lokomotiv", "Krylia Sovetov", Instant.now()),
                MatchResponse.class);
        Long matchId = created.getBody().id();
        publish(matchId, new CreateEventRequest(EventType.MATCH_STARTED, null, null, 0));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getEvents(matchId).content()).hasSize(1));

        rest.delete("/matches/{id}", matchId);

        ResponseEntity<String> getAfterDelete = rest.getForEntity("/matches/{id}", String.class, matchId);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void eventsAreReturnedPageByPage() {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Ural", "Akhmat", Instant.now()),
                MatchResponse.class);
        Long matchId = created.getBody().id();
        publish(matchId, new CreateEventRequest(EventType.MATCH_STARTED, null, null, 0));
        publish(matchId, new CreateEventRequest(EventType.GOAL, TeamSide.HOME, "A", 10));
        publish(matchId, new CreateEventRequest(EventType.GOAL, TeamSide.AWAY, "B", 20));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getEvents(matchId).content()).hasSize(3));

        PageResponse<EventResponse> firstPage = getEvents(matchId, 0, 2);
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        PageResponse<EventResponse> secondPage = getEvents(matchId, 1, 2);
        assertThat(secondPage.content()).hasSize(1);
    }

    @Test
    void eventForUnknownMatchReturns404() {
        ResponseEntity<String> response = rest.postForEntity(
                "/matches/999999/events",
                new CreateEventRequest(EventType.GOAL, TeamSide.HOME, "Nobody", 1),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void publish(Long matchId, CreateEventRequest request) {
        ResponseEntity<EventAcceptedResponse> response = rest.postForEntity(
                "/matches/{id}/events", request, EventAcceptedResponse.class, matchId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private PageResponse<EventResponse> getEvents(Long matchId) {
        return getEvents(matchId, 0, 20);
    }

    private PageResponse<EventResponse> getEvents(Long matchId, int page, int size) {
        ResponseEntity<PageResponse<EventResponse>> response = rest.exchange(
                "/matches/{id}/events?page={page}&size={size}", HttpMethod.GET, null,
                new ParameterizedTypeReference<PageResponse<EventResponse>>() {}, matchId, page, size);
        return response.getBody();
    }
}
