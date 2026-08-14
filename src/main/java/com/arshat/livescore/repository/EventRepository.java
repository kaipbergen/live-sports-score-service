package com.arshat.livescore.repository;

import com.arshat.livescore.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByMatchIdOrderByCreatedAtAsc(Long matchId, Pageable pageable);

    boolean existsByExternalId(UUID externalId);

    void deleteByMatchId(Long matchId);
}
