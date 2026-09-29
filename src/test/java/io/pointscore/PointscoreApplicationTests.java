package io.pointscore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the whole application against a real Postgres.
 *
 * <p>This is the cheapest test in the suite and one of the most valuable:
 * because {@code ddl-auto: validate} is on, the context only starts if every
 * one of the entity mappings agrees with the tables Flyway created. A column
 * renamed in a migration but not in the entity fails here, at build time,
 * rather than on the first request in production.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PointscoreApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the context starts, which means every entity matches the schema")
    void contextLoads() {
    }

    @Test
    @DisplayName("the migrations ran and the reference data is present")
    void referenceDataIsSeeded() {
        assertThat(jdbc.queryForObject("select count(*) from tiers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from earn_rules where active", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from rewards", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("the ledger really is append-only, enforced by the database")
    void ledgerRejectsMutation() {
        jdbc.update("""
                insert into members (name, email, tier_id)
                values ('Immutability Test', 'immutable@example.com',
                        (select id from tiers where code = 'SILVER'))
                """);
        Long memberId = jdbc.queryForObject(
                "select id from members where email = 'immutable@example.com'", Long.class);

        jdbc.update("""
                insert into ledger_entries (member_id, entry_type, points, description)
                values (?, 'ADJUST', 100, 'seeded by test')
                """, memberId);

        // The guarantee has to hold against raw SQL, not merely against code
        // that politely goes through JPA. Otherwise it is a convention, not a
        // guarantee.
        assertThatThrownBy(() -> jdbc.update(
                "update ledger_entries set points = 999999 where member_id = ?", memberId))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "delete from ledger_entries where member_id = ?", memberId))
                .hasMessageContaining("append-only");

        assertThat(jdbc.queryForObject(
                "select points from ledger_entries where member_id = ?", Integer.class, memberId))
                .isEqualTo(100);
    }
}
