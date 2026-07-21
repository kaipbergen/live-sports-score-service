package com.arshat.livescore.repository;

import com.arshat.livescore.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
}
