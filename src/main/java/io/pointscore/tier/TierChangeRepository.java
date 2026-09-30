package io.pointscore.tier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TierChangeRepository extends JpaRepository<TierChange, Long> {

    List<TierChange> findByMemberIdOrderByChangedAtDescIdDesc(Long memberId);
}
