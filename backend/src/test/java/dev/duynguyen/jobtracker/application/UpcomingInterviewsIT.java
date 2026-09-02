package dev.duynguyen.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import dev.duynguyen.jobtracker.application.dto.UpcomingInterviewResponse;
import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageFormat;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;
import dev.duynguyen.jobtracker.support.Fixtures;

/**
 * The upcoming-interviews aggregation (SCHEMA.md §10.4) against a real MongoDB.
 *
 * <p>An aggregation cannot be tested with mocks — the pipeline either produces the right
 * documents or it does not, and only the server knows which. The case this file exists for
 * is {@link #straddlingApplicationIsNotAnInterview()}: the multikey range trap that a
 * single-{@code $match} pipeline gets wrong and that no unit test would ever catch.
 */
class UpcomingInterviewsIT extends AbstractMongoIT {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    /** With {@code days = 7} the window is 2026-09-01T15:00Z through 2026-09-08T15:00Z. */
    private static final Instant NOW = Instant.parse("2026-09-01T15:00:00Z");

    @Autowired private MongoTemplate mongo;

    private ApplicationQueryService service;

    @BeforeEach
    void setUp() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());
        service = new ApplicationQueryService(
                mongo, new ApplicationMapper(), new TimeService(Clock.fixed(NOW, ZoneId.of("UTC")), ZONE));
        seed();
    }

    /**
     * <pre>
     *  A  technical scheduled 09-04 16:00              → in
     *  B  scheduled 09-08 15:00, the exact cutoff      → in, inclusive boundary
     *  C  scheduled 09-08 15:00:01, one second past    → out
     *  D  scheduled 08-30, already happened            → out
     *  E  scheduled 09-03 but status EXPECTED          → out
     *  F  scheduled 09-05 but application WITHDRAWN    → out
     *  G  scheduled 2026-01-05 and 2027-06-01          → out (the straddle trap)
     *  H  scheduled 09-02 09:00 and 09-06 14:00        → two rows
     * </pre>
     */
    private void seed() {
        mongo.save(Fixtures.application("A").company("c-stripe", "Stripe")
                .stage(StageType.APPLICATION_SUBMITTED, StageStatus.PASSED, null, "2026-08-01T04:00:00Z")
                .stage(StageType.TECHNICAL_INTERVIEW, StageStatus.SCHEDULED, "2026-09-04T16:00:00Z", null)
                .build());

        mongo.save(Fixtures.application("B")
                .stage(StageType.SUPERDAY, StageStatus.SCHEDULED, "2026-09-08T15:00:00Z", null).build());

        mongo.save(Fixtures.application("C")
                .stage(StageType.SUPERDAY, StageStatus.SCHEDULED, "2026-09-08T15:00:01Z", null).build());

        mongo.save(Fixtures.application("D")
                .stage(StageType.PHONE_SCREEN, StageStatus.SCHEDULED, "2026-08-30T10:00:00Z", null).build());

        mongo.save(Fixtures.application("E")
                .stage(StageType.SYSTEM_DESIGN, StageStatus.EXPECTED, "2026-09-03T10:00:00Z", null).build());

        mongo.save(Fixtures.application("F").status(ApplicationStatus.WITHDRAWN)
                .stage(StageType.HIRING_MANAGER, StageStatus.SCHEDULED, "2026-09-05T10:00:00Z", null).build());

        mongo.save(Fixtures.application("G")
                .stage(StageType.PHONE_SCREEN, StageStatus.SCHEDULED, "2026-01-05T10:00:00Z", null)
                .stage(StageType.SUPERDAY, StageStatus.SCHEDULED, "2027-06-01T10:00:00Z", null).build());

        mongo.save(Fixtures.application("H")
                .stage(StageType.BEHAVIORAL, StageStatus.SCHEDULED, "2026-09-02T09:00:00Z", null)
                .stage(StageType.TECHNICAL_INTERVIEW, StageStatus.SCHEDULED, "2026-09-06T14:00:00Z", null)
                .build());
    }

    @Test
    @DisplayName("returns one row per scheduled round, ordered by time across applications")
    void ordersAcrossApplications() {
        List<UpcomingInterviewResponse> interviews = service.upcomingInterviews(7);

        assertThat(interviews).extracting(UpcomingInterviewResponse::role)
                .containsExactly("H", "A", "H", "B");
        assertThat(interviews).extracting(UpcomingInterviewResponse::scheduledAt)
                .containsExactly(
                        Instant.parse("2026-09-02T09:00:00Z"),
                        Instant.parse("2026-09-04T16:00:00Z"),
                        Instant.parse("2026-09-06T14:00:00Z"),
                        Instant.parse("2026-09-08T15:00:00Z"));
    }

    @Test
    @DisplayName("the far edge of the window is inclusive, one second past it is not")
    void windowBoundary() {
        assertThat(service.upcomingInterviews(7)).extracting(UpcomingInterviewResponse::role)
                .contains("B")
                .doesNotContain("C");
    }

    @Test
    @DisplayName("rounds that already happened are not upcoming")
    void pastRoundsExcluded() {
        assertThat(service.upcomingInterviews(7)).extracting(UpcomingInterviewResponse::role)
                .doesNotContain("D");
    }

    @Test
    @DisplayName("only SCHEDULED counts — a pencilled-in round is not an appointment")
    void onlyScheduledStatus() {
        // E has a date but status EXPECTED: you think it is coming, nobody has booked it.
        assertThat(service.upcomingInterviews(7)).extracting(UpcomingInterviewResponse::role)
                .doesNotContain("E");
    }

    @Test
    @DisplayName("a withdrawn application's leftover round is stale data, not a calendar entry")
    void terminalApplicationExcluded() {
        assertThat(service.upcomingInterviews(7)).extracting(UpcomingInterviewResponse::role)
                .doesNotContain("F");
    }

    @Test
    @DisplayName("an application straddling the window with nothing inside it returns nothing")
    void straddlingApplicationIsNotAnInterview() {
        // G's rounds are in January 2026 and June 2027 — nothing in this week. It clears the
        // pre-unwind $match anyway, because on an array Mongo lets one element satisfy the
        // lower bound and a different one the upper. The second $match, after $unwind, is
        // what makes the answer correct. Delete that stage and this test is the only thing
        // that fails.
        assertThat(service.upcomingInterviews(7)).extracting(UpcomingInterviewResponse::role)
                .doesNotContain("G");
    }

    @Test
    @DisplayName("projection carries the identifiers and detail the caller needs")
    void projectionShape() {
        UpcomingInterviewResponse technical = service.upcomingInterviews(7).stream()
                .filter(i -> i.role().equals("A"))
                .findFirst()
                .orElseThrow();

        assertThat(technical.applicationId()).isNotBlank();
        assertThat(technical.stageId()).isEqualTo("stage-2");
        assertThat(technical.companyId()).isEqualTo("c-stripe");
        assertThat(technical.companyName()).isEqualTo("Stripe");
        assertThat(technical.stageType()).isEqualTo(StageType.TECHNICAL_INTERVIEW);
        assertThat(technical.format()).isEqualTo(StageFormat.VIDEO);
        assertThat(technical.interviewers()).containsExactly("Priya N. (Staff Eng)");
    }

    @Test
    @DisplayName("a wider window reaches rounds a narrow one misses, but never into the past")
    void widerWindow() {
        List<UpcomingInterviewResponse> year = service.upcomingInterviews(365);

        // 365 days from 2026-09-01 runs to 2027-09-01, so G's June 2027 superday is now a
        // real hit rather than the straddle artefact it was at 7 days. Its January 2026
        // round stays out at any width: the lower bound is always now.
        assertThat(year).extracting(UpcomingInterviewResponse::scheduledAt)
                .contains(Instant.parse("2027-06-01T10:00:00Z"))
                .doesNotContain(Instant.parse("2026-01-05T10:00:00Z"));

        // C sits one second past the seven-day cutoff and appears here, confirming the
        // narrow window excluded it on time rather than on some other property.
        assertThat(year).extracting(UpcomingInterviewResponse::role).contains("C");
    }

    @Test
    @DisplayName("an oversized window is rejected rather than scanning the whole future")
    void rejectsOversizedWindow() {
        assertThatThrownBy(() -> service.upcomingInterviews(366))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a non-positive window is rejected rather than silently returning nothing")
    void rejectsNonPositiveDays() {
        assertThatThrownBy(() -> service.upcomingInterviews(0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upcomingInterviews(-7)).isInstanceOf(BadRequestException.class);
    }
}
