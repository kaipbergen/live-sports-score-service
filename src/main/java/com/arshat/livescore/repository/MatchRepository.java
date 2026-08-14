package com.arshat.livescore.repository;

import com.arshat.livescore.domain.Match;
import com.arshat.livescore.domain.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Page<Match> findByStatus(MatchStatus status, Pageable pageable);
}
