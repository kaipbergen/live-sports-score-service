package com.arshat.livescore;

import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.domain.TeamSide;
import com.arshat.livescore.dto.BatchCreateMatchRequest;
import com.arshat.livescore.dto.CreateEventRequest;
import com.arshat.livescore.dto.CreateMatchRequest;
import com.arshat.livescore.dto.EventAcceptedResponse;
import com.arshat.livescore.dto.EventResponse;
import com.arshat.livescore.dto.MatchResponse;
import com.arshat.livescore.dto.PageResponse;
import com.arshat.livescore.dto.ScoreResponse;
import com.arshat.livescore.dto.UpdateMatchStatusRequest;
import com.arshat.livescore.domain.Match;
import com.arshat.livescore.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void staleMatchUpdateFailsOptimisticLock() {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Torpedo", "Khimki", Instant.now()),
                MatchResponse.class);
        Long matchId = created.getBody().id();

        Match copy1 = matchRepository.findById(matchId).orElseThrow();
        Match copy2 = matchRepository.findById(matchId).orElseThrow();

        copy1.setStatus(MatchStatus.LIVE);
        matchRepository.save(copy1);

        copy2.setStatus(MatchStatus.FINISHED);
        assertThatThrownBy(() -> matchRepository.save(copy2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

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
    void matchesCanBeFilteredByStatus() {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Sochi", "Fakel", Instant.now()),
                MatchResponse.class);
        Long matchId = created.getBody().id();
        rest.patchForObject(
                "/matches/{id}", new UpdateMatchStatusRequest(MatchStatus.LIVE), MatchResponse.class, matchId);

        PageResponse<MatchResponse> liveMatches = getMatches(MatchStatus.LIVE, 0, 200);
        assertThat(liveMatches.content()).extracting(MatchResponse::id).contains(matchId);
        assertThat(liveMatches.content()).allSatisfy(m -> assertThat(m.status()).isEqualTo(MatchStatus.LIVE));

        PageResponse<MatchResponse> finishedMatches = getMatches(MatchStatus.FINISHED, 0, 200);
        assertThat(finishedMatches.content()).extracting(MatchResponse::id).doesNotContain(matchId);
    }

    @Test
    void eventsCanBeFilteredByType() {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Rostov", "Orenburg", Instant.now()),
                MatchResponse.class);
        Long matchId = created.getBody().id();
        publish(matchId, new CreateEventRequest(EventType.MATCH_STARTED, null, null, 0));
        publish(matchId, new CreateEventRequest(EventType.GOAL, TeamSide.HOME, "A", 10));
        publish(matchId, new CreateEventRequest(EventType.GOAL, TeamSide.AWAY, "B", 20));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(getEvents(matchId).content()).hasSize(3));

        PageResponse<EventResponse> goals = getEventsByType(matchId, EventType.GOAL);
        assertThat(goals.content()).hasSize(2);
        assertThat(goals.content()).extracting(EventResponse::type).containsOnly(EventType.GOAL);

        PageResponse<EventResponse> started = getEventsByType(matchId, EventType.MATCH_STARTED);
        assertThat(started.content()).hasSize(1);
    }

    @Test
    void updatingMatchStatusBumpsUpdatedAt() throws InterruptedException {
        ResponseEntity<MatchResponse> created = rest.postForEntity(
                "/matches",
                new CreateMatchRequest("Akademia", "Tambov", Instant.now()),
                MatchResponse.class);
        MatchResponse initial = created.getBody();
        assertThat(initial.createdAt()).isNotNull();
        assertThat(initial.updatedAt()).isNotNull();

        Thread.sleep(50);
        MatchResponse updated = rest.patchForObject(
                "/matches/{id}", new UpdateMatchStatusRequest(MatchStatus.LIVE),
                MatchResponse.class, initial.id());

        assertThat(updated.createdAt()).isEqualTo(initial.createdAt());
        assertThat(updated.updatedAt()).isAfter(initial.updatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void actuatorInfoExposesBuildMetadata() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/actuator/info", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> build = (Map<String, Object>) response.getBody().get("build");
        assertThat(build).isNotNull();
        assertThat(build.get("artifact")).isEqualTo("live-score-service");
        assertThat(build.get("version")).isEqualTo("1.0.0");
    }

    @Test
    void oversizedRequestBodyIsRejected() {
        String hugeTeamName = "A".repeat(3 * 1024 * 1024);
        ResponseEntity<String> response = rest.postForEntity(
                "/matches",
                new CreateMatchRequest(hugeTeamName, "Opponent", Instant.now()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void batchCreatesMultipleMatches() {
        BatchCreateMatchRequest batch = new BatchCreateMatchRequest(List.of(
                new CreateMatchRequest("Krasnodar", "Ufa", Instant.now()),
                new CreateMatchRequest("Nizhny Novgorod", "Baltika", Instant.now())));

        ResponseEntity<List<MatchResponse>> response = rest.exchange(
                "/matches/batch", HttpMethod.POST, new HttpEntity<>(batch),
                new ParameterizedTypeReference<List<MatchResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<MatchResponse> created = response.getBody();
        assertThat(created).hasSize(2);
        assertThat(created).extracting(MatchResponse::homeTeam).containsExactly("Krasnodar", "Nizhny Novgorod");
        assertThat(created).allSatisfy(m -> assertThat(m.id()).isNotNull());

        ResponseEntity<MatchResponse> fetched = rest.getForEntity(
                "/matches/{id}", MatchResponse.class, created.get(0).id());
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void batchCreationRejectsEmptyList() {
        ResponseEntity<String> response = rest.exchange(
                "/matches/batch", HttpMethod.POST,
                new HttpEntity<>(new BatchCreateMatchRequest(List.of())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void repeatingIdempotencyKeyReturnsTheSameMatchInstead() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "match-create-" + java.util.UUID.randomUUID());
        HttpEntity<CreateMatchRequest> request = new HttpEntity<>(
                new CreateMatchRequest("Spartak", "Zenit", Instant.now()), headers);

        ResponseEntity<MatchResponse> first = rest.postForEntity("/matches", request, MatchResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long matchId = first.getBody().id();

        ResponseEntity<MatchResponse> replay = rest.postForEntity("/matches", request, MatchResponse.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(matchId);

        PageResponse<MatchResponse> allMatches = getMatches(null, 0, 500);
        assertThat(allMatches.content()).extracting(MatchResponse::id).filteredOn(matchId::equals).hasSize(1);
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

    private PageResponse<MatchResponse> getMatches(MatchStatus status, int page, int size) {
        ResponseEntity<PageResponse<MatchResponse>> response = rest.exchange(
                "/matches?status={status}&page={page}&size={size}", HttpMethod.GET, null,
                new ParameterizedTypeReference<PageResponse<MatchResponse>>() {}, status, page, size);
        return response.getBody();
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

    private PageResponse<EventResponse> getEventsByType(Long matchId, EventType type) {
        ResponseEntity<PageResponse<EventResponse>> response = rest.exchange(
                "/matches/{id}/events?type={type}", HttpMethod.GET, null,
                new ParameterizedTypeReference<PageResponse<EventResponse>>() {}, matchId, type);
        return response.getBody();
    }
}
