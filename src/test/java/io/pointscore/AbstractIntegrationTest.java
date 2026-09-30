package io.pointscore;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for tests that need the full application and a real Postgres.
 *
 * <p>Deliberately not {@code @Transactional}. Spring's transactional tests roll
 * back at the end, which is convenient -- and fatal for anything testing
 * concurrency, because work that is never committed is invisible to other
 * threads, and a row lock held by the test's own transaction would deadlock
 * against the code under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
