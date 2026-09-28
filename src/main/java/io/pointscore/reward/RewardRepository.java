package io.pointscore.reward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    Optional<Reward> findByCode(String code);

    List<Reward> findByActiveTrueOrderByCostPointsAsc();

    /**
     * Decrements stock only if there is stock to take, and reports whether it
     * won. One atomic statement, so two members racing for the last mug cannot
     * both succeed: Postgres serialises the row update, the loser matches zero
     * rows and gets {@code 0} back.
     *
     * <p>Doing this as read-check-write in Java instead would reintroduce
     * exactly the race this avoids.
     */
    @Modifying
    @Query("""
            update Reward r set r.stock = r.stock - 1, r.updatedAt = current_timestamp
            where r.id = :rewardId and r.stock > 0
            """)
    int decrementStock(Long rewardId);
}
