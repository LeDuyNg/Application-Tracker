package dev.duynguyen.jobtracker.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import dev.duynguyen.jobtracker.application.Application;
import dev.duynguyen.jobtracker.application.Stage;
import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationSource;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.stats.dto.StatsResponse;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * Exercises the real {@code $facet} pipeline against a real MongoDB.
 *
 * <p>A mocked test cannot validate an aggregation — the pipeline either produces the right
 * documents or it does not, and only the server knows. The fixture below is small enough to
 * verify every expected number by hand, which is the point: each assertion has an arithmetic
 * derivation in a comment rather than being whatever the code happened to emit.
 */
class StatsServiceIT extends AbstractMongoIT {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    /** Fixed "today" so the 21-day ghost cutoff (2026-08-11) is deterministic. */
    private static final Instant NOW = Instant.parse("2026-09-01T15:00:00Z");

    @Autowired private MongoTemplate mongo;

    private StatsService service;

    @BeforeEach
    void setUp() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());
        service = new StatsService(mongo, new TimeService(Clock.fixed(NOW, ZoneId.of("UTC")), ZONE));
        seed();
    }

    /**
     * Five applications, chosen so every metric has at least one edge case:
     *
     * <pre>
     *  A  applied 08-02  submitted, OA passed, technical scheduled   ACTIVE    replied
     *  B  applied 08-02  submitted, OA failed                        REJECTED  replied
     *  C  applied 08-01  submitted only, older than the cutoff       ACTIVE    ghosted
     *  D  applied 08-25  submitted only, newer than the cutoff       ACTIVE    too new to be ghosted
     *  E  applied 07-15  submitted, recruiter, offer all passed      OFFER     replied
     * </pre>
     */
    private void seed() {
        mongo.save(application("A", LocalDate.of(2026, 8, 2), ApplicationStatus.ACTIVE,
                stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-08-02T04:00:00Z"),
                stage(2, StageType.ONLINE_ASSESSMENT, StageStatus.PASSED, null, "2026-08-07T18:00:00Z"),
                stage(3, StageType.TECHNICAL_INTERVIEW, StageStatus.SCHEDULED, "2026-09-04T16:00:00Z", null)));

        mongo.save(application("B", LocalDate.of(2026, 8, 2), ApplicationStatus.REJECTED,
                stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-08-02T04:00:00Z"),
                stage(2, StageType.ONLINE_ASSESSMENT, StageStatus.FAILED, null, "2026-08-06T20:00:00Z")));

        mongo.save(application("C", LocalDate.of(2026, 8, 1), ApplicationStatus.ACTIVE,
                stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-08-01T04:00:00Z")));

        mongo.save(application("D", LocalDate.of(2026, 8, 25), ApplicationStatus.ACTIVE,
                stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-08-25T04:00:00Z")));

        mongo.save(application("E", LocalDate.of(2026, 7, 15), ApplicationStatus.OFFER,
                stage(1, StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-07-15T04:00:00Z"),
                stage(2, StageType.RECRUITER_SCREEN, StageStatus.PASSED, null, "2026-07-20T15:00:00Z"),
                stage(3, StageType.OFFER, StageStatus.PASSED, null, "2026-08-01T12:00:00Z")));
    }

    @Test
    @DisplayName("counts and status breakdown")
    void countsAndStatuses() {
        StatsResponse stats = service.compute(null, null, null);

        assertThat(stats.totalApplications()).isEqualTo(5);
        assertThat(stats.byStatus())
                .containsEntry(ApplicationStatus.ACTIVE, 3L)      // A, C, D
                .containsEntry(ApplicationStatus.REJECTED, 1L)    // B
                .containsEntry(ApplicationStatus.OFFER, 1L);      // E
        assertThat(stats.activePipeline()).isEqualTo(3);
    }

    @Test
    @DisplayName("funnel reports the furthest stage each application passed, in progression order")
    void funnel() {
        StatsResponse stats = service.compute(null, null, null);

        // B, C, D got no further than the submission; A passed the OA; E passed the offer.
        assertThat(stats.funnel()).containsExactly(
                new StatsResponse.FunnelEntry(StageType.APPLICATION_SUBMITTED, 3L),
                new StatsResponse.FunnelEntry(StageType.ONLINE_ASSESSMENT, 1L),
                new StatsResponse.FunnelEntry(StageType.OFFER, 1L));
    }

    @Test
    @DisplayName("response rate counts anything past the initial submission")
    void responseRate() {
        // A, B and E have a second stage; C and D do not. 3/5.
        assertThat(service.compute(null, null, null).responseRatePct()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("ghost rate divides by applications old enough to be ghosted, not by all")
    void ghostRate() {
        // Cutoff is 2026-08-11. Eligible: A, B, C, E (D was applied 08-25, too recent).
        // Of those, only C is ACTIVE with a single stage. 1/4 = 25%, not 1/5 = 20%.
        assertThat(service.compute(null, null, null).ghostRatePct()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("offer rate counts OFFER and ACCEPTED")
    void offerRate() {
        assertThat(service.compute(null, null, null).offerRatePct()).isEqualTo(20.0); // E only
    }

    @Test
    @DisplayName("average days to first response")
    void avgDaysToFirstResponse() {
        // A: 08-02T00:00Z -> 08-07T18:00Z = 5.75 d
        // B: 08-02T00:00Z -> 08-06T20:00Z = 4.8333 d
        // E: 07-15T00:00Z -> 07-20T15:00Z = 5.625 d
        // mean = 16.2083 / 3 = 5.4027 -> 5.4
        assertThat(service.compute(null, null, null).avgDaysToFirstResponse()).isEqualTo(5.4);
    }

    @Test
    @DisplayName("a rolling window excludes applications outside it and says so")
    void rollingWindow() {
        // 30 days back from 2026-09-01 is 2026-08-02, so E (07-15) and C (08-01) drop out.
        StatsResponse stats = service.compute(30, null, null);

        assertThat(stats.totalApplications()).isEqualTo(3); // A, B, D
        assertThat(stats.window().description()).isEqualTo("the last 30 days");
        assertThat(stats.window().from()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    @DisplayName("an explicit from/to window answers the calendar-month question")
    void calendarWindow() {
        StatsResponse stats = service.compute(null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(stats.totalApplications()).isEqualTo(4); // A, B, C, D — not E (July)
        assertThat(stats.window().description()).isEqualTo("2026-08-01 to 2026-08-31");
    }

    @Test
    @DisplayName("days and from/to are mutually exclusive")
    void rejectsBothWindowForms() {
        assertThatThrownBy(() -> service.compute(30, LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("an empty database yields zeroes, not divide-by-zero")
    void emptyDatabase() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());

        StatsResponse stats = service.compute(null, null, null);

        assertThat(stats.totalApplications()).isZero();
        assertThat(stats.responseRatePct()).isZero();
        assertThat(stats.ghostRatePct()).isZero();
        assertThat(stats.avgDaysToFirstResponse()).isNull();
        assertThat(stats.funnel()).isEmpty();
    }

    // ------------------------------------------------------------- fixture

    private Application application(String role, LocalDate appliedDate, ApplicationStatus status, Stage... stages) {
        Application a = new Application();
        a.setCompanyId("company-1");
        a.setCompanyName("Stripe");
        a.setRole(role);
        a.setStatus(status);
        a.setAppliedDate(appliedDate);
        a.setSource(ApplicationSource.COLD_APPLY);
        a.setStages(List.of(stages));
        return a;
    }

    private Stage stage(int sequence, StageType type, StageStatus status, String scheduledAt, String completedAt) {
        Stage s = new Stage();
        s.setStageId("stage-" + sequence);
        s.setSequence(sequence);
        s.setType(type);
        s.setStatus(status);
        s.setScheduledAt(scheduledAt == null ? null : Instant.parse(scheduledAt));
        s.setCompletedAt(completedAt == null ? null : Instant.parse(completedAt));
        return s;
    }
}
