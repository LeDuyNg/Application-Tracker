package dev.duynguyen.jobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Entry point for the Job Application Tracker API.
 *
 * <p>{@code @EnableMongoAuditing} switches on the {@code @CreatedDate} /
 * {@code @LastModifiedDate} support that {@code Company} and {@code Application} rely on
 * (SCHEMA.md §11). Without it those fields stay null and nothing warns you.
 *
 * <p>{@code @ConfigurationPropertiesScan} registers {@code AppProperties} (the {@code app.*}
 * prefix) without needing an explicit {@code @EnableConfigurationProperties} listing.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMongoAuditing
public class JobTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobTrackerApplication.class, args);
    }

}
