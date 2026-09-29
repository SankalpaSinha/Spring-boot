package io.pointscore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real Postgres for tests, started in Docker and thrown away afterwards.
 *
 * <p>The alternative -- an in-memory H2 -- is faster and lies. It has no
 * {@code SELECT ... FOR UPDATE} worth the name, different transaction
 * behaviour, no jsonb, and no triggers. Every hard thing in this project is
 * something H2 would let pass and Postgres would catch, so the tests run
 * against the same database production does.
 *
 * <p>{@link ServiceConnection} wires the container's generated URL, username and
 * password into Spring's datasource automatically, so nothing here has to be
 * repeated in a properties file.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Pinned deliberately, and matching compose.yaml. {@code postgres:latest}
     * means "whatever was published most recently", so a green build today can
     * fail tomorrow with no change from you -- and it silently diverges from
     * the version you actually run locally.
     */
    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("pointscore")
                .withUsername("pointscore")
                .withPassword("pointscore")
                // Keeps the container alive between runs when the developer has
                // opted in via ~/.testcontainers.properties. Saves ~2s per run
                // locally; ignored on CI, which has no such file.
                .withReuse(true);
    }
}
