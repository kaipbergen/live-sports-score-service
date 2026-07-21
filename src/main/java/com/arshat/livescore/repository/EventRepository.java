package com.arshat.livescore.repository;

import com.arshat.livescore.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByMatchIdOrderByCreatedAtAsc(Long matchId);

    boolean existsByExternalId(UUID externalId);
}
