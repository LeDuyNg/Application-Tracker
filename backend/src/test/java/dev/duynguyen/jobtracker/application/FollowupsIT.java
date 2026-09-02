package dev.duynguyen.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import dev.duynguyen.jobtracker.application.dto.FollowupResponse;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;
import dev.duynguyen.jobtracker.support.Fixtures;

/**
 * The follow-ups query (SCHEMA.md §10.2) against a real MongoDB.
 *
 * <p>Every assertion here is a boundary or an exclusion, because that is where this query
 * goes wrong: a threshold that is exclusive when it should be inclusive, or a closed
 * application that keeps nagging. "Now" is fixed so the boundaries are exact dates rather
 * than something the wall clock decides.
 */
class FollowupsIT extends AbstractMongoIT {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    /**
     * 11:00 in New York on 2026-09-01. The due cutoff is therefore 2026-09-08 and the
     * gone-quiet cutoff 2026-08-18T15:00Z.
     */
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
     * Nine applications, one per rule the query has to get right:
     *
     * <pre>
     *  A  followUp 08-25, quiet since 07-25   → both groups
     *  B  followUp 09-01 (today)              → due only
     *  C  followUp 09-08 (exactly the cutoff) → due only, inclusive boundary
     *  D  followUp 09-09 (one day past)       → neither
     *  E  followUp 08-20 but REJECTED         → neither, terminal
     *  F  no followUp, recent contact         → neither
     *  G  quiet since 08-18T15:00 (exactly)   → gone quiet, inclusive boundary
     *  H  quiet since 08-19T15:00 (13 days)   → neither
     *  I  quiet since 07-01 but OFFER         → neither, not ACTIVE
     * </pre>
     */
    private void seed() {
        mongo.save(Fixtures.application("A").followUp("2026-08-25").lastContact("2026-07-25T15:00:00Z").build());
        mongo.save(Fixtures.application("B").followUp("2026-09-01").lastContact("2026-08-31T15:00:00Z").build());
        mongo.save(Fixtures.application("C").followUp("2026-09-08").lastContact("2026-08-31T15:00:00Z").build());
        mongo.save(Fixtures.application("D").followUp("2026-09-09").lastContact("2026-08-31T15:00:00Z").build());
        mongo.save(Fixtures.application("E").status(ApplicationStatus.REJECTED)
                .followUp("2026-08-20").lastContact("2026-07-01T15:00:00Z").build());
        mongo.save(Fixtures.application("F").lastContact("2026-08-31T15:00:00Z").build());
        mongo.save(Fixtures.application("G").lastContact("2026-08-18T15:00:00Z").build());
        mongo.save(Fixtures.application("H").lastContact("2026-08-19T15:00:00Z").build());
        mongo.save(Fixtures.application("I").status(ApplicationStatus.OFFER)
                .lastContact("2026-07-01T15:00:00Z").build());
    }

    @Test
    @DisplayName("due list looks a week ahead, inclusive of the cutoff day, soonest first")
    void dueWindow() {
        FollowupResponse response = service.followups();

        assertThat(response.due()).extracting(d -> d.application().role())
                .containsExactly("A", "B", "C");   // sorted by followUpDate ascending
        assertThat(response.dueWithinDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("daysOverdue is positive when past due and negative when still upcoming")
    void daysOverdueSign() {
        FollowupResponse response = service.followups();

        assertThat(response.due()).extracting(FollowupResponse.Due::daysOverdue)
                .containsExactly(7L, 0L, -7L);     // A was due a week ago; C is due in a week
    }

    @Test
    @DisplayName("a terminal application never appears, however overdue")
    void terminalExcluded() {
        // E's followUpDate is 12 days past, which would place it first if status were ignored.
        assertThat(service.followups().due()).extracting(d -> d.application().role()).doesNotContain("E");
    }

    @Test
    @DisplayName("an application with no followUpDate is not due")
    void nullFollowUpDateExcluded() {
        // Guards the implicit behaviour the query relies on: null is not $lte any date, so
        // no exists clause is needed. If that ever stopped holding, every application with
        // no reminder set would flood the list.
        assertThat(service.followups().due()).extracting(d -> d.application().role())
                .doesNotContain("F", "G", "H", "I");
    }

    @Test
    @DisplayName("gone quiet is ACTIVE only, at 14 days exactly, quietest first")
    void goneQuietWindow() {
        FollowupResponse response = service.followups();

        assertThat(response.goneQuiet()).extracting(q -> q.application().role())
                .containsExactly("A", "G");        // sorted by lastContactAt ascending
        assertThat(response.goneQuiet()).extracting(FollowupResponse.GoneQuiet::daysSinceContact)
                .containsExactly(38L, 14L);
        assertThat(response.quietAfterDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("13 days of silence is not yet quiet")
    void justInsideTheQuietThreshold() {
        assertThat(service.followups().goneQuiet()).extracting(q -> q.application().role())
                .doesNotContain("H");
    }

    @Test
    @DisplayName("an offer you are sitting on is not a company that has gone quiet")
    void nonActiveExcludedFromGoneQuiet() {
        // I has been silent for two months, but the silence is yours, not theirs.
        assertThat(service.followups().goneQuiet()).extracting(q -> q.application().role())
                .doesNotContain("I", "E");
    }

    @Test
    @DisplayName("the two groups are not deduplicated against each other")
    void anApplicationCanBeInBothGroups() {
        FollowupResponse response = service.followups();

        // A is both due and silent — the most urgent case, and it must not be swallowed by
        // whichever list happened to claim it first.
        assertThat(response.due()).extracting(d -> d.application().role()).contains("A");
        assertThat(response.goneQuiet()).extracting(q -> q.application().role()).contains("A");
    }

    @Test
    @DisplayName("an empty database yields empty groups, not null")
    void emptyDatabase() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());

        FollowupResponse response = service.followups();

        assertThat(response.due()).isEmpty();
        assertThat(response.goneQuiet()).isEmpty();
    }
}
