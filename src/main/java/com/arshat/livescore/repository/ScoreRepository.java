package com.arshat.livescore.repository;

import com.arshat.livescore.domain.Score;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    Optional<Score> findByMatchId(Long matchId);

    /**
     * SELECT ... FOR UPDATE — serializes concurrent score updates for the same
     * match across consumer instances/partitions rebalances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Score> findWithLockByMatchId(Long matchId);
}
