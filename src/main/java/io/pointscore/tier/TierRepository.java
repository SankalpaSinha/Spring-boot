package io.pointscore.tier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TierRepository extends JpaRepository<Tier, Long> {

    Optional<Tier> findByCode(String code);

    /** Lowest tier first -- the one every new member starts in. */
    List<Tier> findAllByOrderBySortOrderAsc();

    /**
     * The best tier a member with this many rolling-12-month points qualifies
     * for. Resolved in SQL rather than by loading every tier and filtering in
     * Java, so it stays correct as tiers are added.
     */
    @Query("""
            select t from Tier t
            where t.minPoints12m <= :earnedPoints
            order by t.minPoints12m desc
            limit 1
            """)
    Optional<Tier> findHighestQualifying(int earnedPoints);
}
