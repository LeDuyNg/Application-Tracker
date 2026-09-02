package dev.duynguyen.jobtracker;

import org.junit.jupiter.api.Test;

import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * Context-load smoke test: every bean wires, the two security chains build, and the
 * application actually starts.
 *
 * <p><strong>This is an {@code *IT}, not a {@code *Test}, and that was a correction.</strong>
 * It began life as {@code JobTrackerApplicationTests} under Surefire, on the reasoning that a
 * context-load check should be fast and need no Docker. That reasoning was wrong here:
 * {@code IndexInitializer} is an {@link org.springframework.boot.ApplicationRunner}, and
 * {@code @SpringBootTest} runs those — so starting the context opens a Mongo connection and
 * creates nine indexes. Without a database the context cannot load. A full-context test that
 * talks to Mongo is an integration test by this project's own definition (CLAUDE.md §11), so
 * it now extends {@link AbstractMongoIT} and gets a real containerised MongoDB.
 *
 * <p><strong>The way this was found is the part worth remembering.</strong> It passed locally
 * for three phases — including under {@code ./mvnw verify}, which is supposed to be the thing
 * that reproduces CI — purely because the developer's {@code jt-mongo} container happens to
 * be listening on {@code localhost:27017}. Spring Data's default URI is
 * {@code mongodb://localhost:27017}, so the test silently used the dev database instead of
 * failing. The first GitHub Actions run, on a machine with no ambient Mongo, failed
 * immediately. See STATE.md §4.
 */
class JobTrackerApplicationIT extends AbstractMongoIT {

    @Test
    void contextLoads() {
    }

}
