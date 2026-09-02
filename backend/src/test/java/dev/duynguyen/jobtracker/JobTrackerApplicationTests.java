package dev.duynguyen.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context-load smoke test.
 *
 * <p>Note the {@code *Tests} suffix: Surefire runs this on {@code mvn test}. Integration
 * tests that need a real MongoDB use the {@code *IT} suffix instead and run under Failsafe
 * on {@code mvn verify} (CLAUDE.md §11).
 *
 * <p>The Google client properties are dummies. From Phase 2 the security config calls
 * {@code oauth2Login()}, which requires a {@code ClientRegistrationRepository}, and Boot only
 * creates one when a registration is configured — so without these the context cannot start
 * and this test fails for a reason that has nothing to do with what it checks.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
})
class JobTrackerApplicationTests {

    @Test
    void contextLoads() {
    }

}
