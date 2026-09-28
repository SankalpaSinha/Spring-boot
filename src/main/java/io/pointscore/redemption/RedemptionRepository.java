package io.pointscore.redemption;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    Optional<Redemption> findByIdempotencyKey(String idempotencyKey);

    Page<Redemption> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
}
