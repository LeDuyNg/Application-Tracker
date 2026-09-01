package dev.duynguyen.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context-load smoke test.
 *
 * <p>Note the {@code *Tests} suffix: Surefire runs this on {@code mvn test}. Integration
 * tests that need a real MongoDB use the {@code *IT} suffix instead and run under Failsafe
 * on {@code mvn verify} (CLAUDE.md §11).
 */
@SpringBootTest
class JobTrackerApplicationTests {

    @Test
    void contextLoads() {
    }

}
