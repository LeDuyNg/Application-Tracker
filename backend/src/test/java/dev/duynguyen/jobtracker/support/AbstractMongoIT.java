package dev.duynguyen.jobtracker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for {@code *IT} tests: a real MongoDB in a container, not an in-memory fake.
 *
 * <p>{@code @ServiceConnection} is what wires the container's port into
 * {@code spring.mongodb.uri} automatically — no {@code @DynamicPropertySource} needed.
 *
 * <p><strong>Started in a static initializer, deliberately not with {@code @Testcontainers}
 * and {@code @Container}.</strong> That annotation pair ties the container's lifecycle to a
 * <em>test class</em>: it stops the container when the class finishes. With a single IT that
 * is invisible, but the moment a second IT class inherits this base, the first class's
 * teardown stops the container while Spring's cached application context still holds the old
 * port — and every later class dies on {@code Connection refused} against a port nothing is
 * listening on. Starting it here instead gives one container for the whole JVM run, matching
 * the lifetime of the context Spring is already caching. Testcontainers' Ryuk sidecar removes
 * it when the JVM exits.
 *
 * <p>Image is {@code mongo:8} to match the Atlas major version — testing against 7 and
 * deploying to 8 is how behavioural differences reach production undetected.
 */
@SpringBootTest(properties = {
        // Phase 2 wires oauth2Login(), which needs a ClientRegistrationRepository — and Boot
        // only creates one when a registration is configured. Without these two lines every
        // @SpringBootTest fails at context creation, not just the security ones. The values
        // are never used: no test completes a Google round trip.
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",

        // A known token so SecurityIT can present the right one and the wrong one. Real
        // tokens live in backend/config/application-local.yml, which tests never read.
        "app.mcp-token=test-mcp-token-0123456789abcdef",
        "app.allowed-emails=allowed@example.com",
})
public abstract class AbstractMongoIT {

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8"));

    static {
        MONGO.start();
    }
}
