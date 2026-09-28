package io.pointscore.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** Matches the lower(email) unique index, so lookup and uniqueness agree. */
    @Query("select m from Member m where lower(m.email) = lower(:email)")
    Optional<Member> findByEmailIgnoringCase(String email);

    @Query("select count(m) > 0 from Member m where lower(m.email) = lower(:email)")
    boolean existsByEmailIgnoringCase(String email);
}
