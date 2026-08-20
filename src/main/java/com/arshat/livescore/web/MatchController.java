package com.arshat.livescore.web;

import com.arshat.livescore.domain.EventType;
import com.arshat.livescore.domain.MatchStatus;
import com.arshat.livescore.dto.BatchCreateMatchRequest;
import com.arshat.livescore.dto.CreateEventRequest;
import com.arshat.livescore.dto.CreateMatchRequest;
import com.arshat.livescore.dto.EventAcceptedResponse;
import com.arshat.livescore.dto.EventResponse;
import com.arshat.livescore.dto.MatchResponse;
import com.arshat.livescore.dto.PageResponse;
import com.arshat.livescore.dto.ScoreResponse;
import com.arshat.livescore.dto.UpdateMatchStatusRequest;
import com.arshat.livescore.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping
    public ResponseEntity<MatchResponse> createMatch(
            @Valid @RequestBody CreateMatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        MatchService.MatchCreationResult result = matchService.createMatch(request, idempotencyKey);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MatchResponse> createMatches(@Valid @RequestBody BatchCreateMatchRequest request) {
        return matchService.createMatches(request.matches());
    }

    @GetMapping
    public PageResponse<MatchResponse> getMatches(@RequestParam(required = false) MatchStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return matchService.getMatches(PageRequest.of(page, size), status);
    }

    @GetMapping("/{id}")
    public MatchResponse getMatch(@PathVariable Long id) {
        return matchService.getMatch(id);
    }

    @PatchMapping("/{id}")
    public MatchResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateMatchStatusRequest request) {
        return matchService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMatch(@PathVariable Long id) {
        matchService.deleteMatch(id);
    }

    @PostMapping("/{id}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse publishEvent(@PathVariable Long id,
                                              @Valid @RequestBody CreateEventRequest request) {
        UUID eventId = matchService.publishEvent(id, request);
        return new EventAcceptedResponse(eventId, "ACCEPTED");
    }

    @GetMapping("/{id}/score")
    public ScoreResponse getScore(@PathVariable Long id) {
        return matchService.getScore(id);
    }

    @GetMapping("/{id}/events")
    public PageResponse<EventResponse> getEvents(@PathVariable Long id,
                                                  @RequestParam(required = false) EventType type,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return matchService.getEvents(id, type, PageRequest.of(page, size));
    }
}
