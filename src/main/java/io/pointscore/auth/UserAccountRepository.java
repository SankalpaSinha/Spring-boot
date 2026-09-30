package io.pointscore.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Written out rather than derived: Spring Data's {@code IgnoreCase} emits
     * {@code upper(email) = upper(?)}, which the {@code lower(email)} unique
     * index cannot serve. Same shape as MemberRepository.
     */
    @Query("select a from UserAccount a where lower(a.email) = lower(:email)")
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
