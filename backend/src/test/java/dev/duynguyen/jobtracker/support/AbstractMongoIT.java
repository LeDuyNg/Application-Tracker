package dev.duynguyen.jobtracker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for {@code *IT} tests: a real MongoDB in a container, not an in-memory fake.
 *
 * <p>{@code @ServiceConnection} is what wires the container's random port into
 * {@code spring.mongodb.uri} automatically — no {@code @DynamicPropertySource} needed.
 *
 * <p>The container is {@code static}, so one instance is reused across every IT class in the
 * run rather than being started per class. Startup is a few seconds (the image initialises a
 * single-node replica set), which is worth paying once.
 *
 * <p>Image is {@code mongo:8} to match the Atlas major version — testing against 7 and
 * deploying to 8 is how behavioural differences reach production undetected.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractMongoIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8"));
}
