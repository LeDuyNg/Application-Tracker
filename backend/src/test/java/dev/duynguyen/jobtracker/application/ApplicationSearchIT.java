package dev.duynguyen.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import dev.duynguyen.jobtracker.application.dto.ApplicationSearchRequest;
import dev.duynguyen.jobtracker.application.dto.ApplicationSummaryResponse;
import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;
import dev.duynguyen.jobtracker.support.Fixtures;

/**
 * Filtered search (SCHEMA.md §10.3) against a real MongoDB.
 *
 * <p>Two groups of tests matter more than the rest. The substring tests are the reason this
 * is a regex and not a {@code $text} index — they are what {@code $text} would fail. The
 * escaping tests are the reason the input goes through {@code Pattern.quote} — without it
 * one of them is a 500 and another silently returns the whole collection.
 */
class ApplicationSearchIT extends AbstractMongoIT {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Instant NOW = Instant.parse("2026-09-01T15:00:00Z");

    /** No sort, so the service's own default has to supply one. */
    private static final Pageable UNSORTED = PageRequest.of(0, 20);

    @Autowired private MongoTemplate mongo;

    private ApplicationQueryService service;

    @BeforeEach
    void setUp() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());
        service = new ApplicationQueryService(
                mongo, new ApplicationMapper(), new TimeService(Clock.fixed(NOW, ZoneId.of("UTC")), ZONE));
        seed();
    }

    private void seed() {
        mongo.save(Fixtures.application("Backend Engineer, Payments").company("c-stripe", "Stripe")
                .applied("2026-08-02").notes("Referred by a former colleague. Team owns the ledger service.")
                .build());

        mongo.save(Fixtures.application("Infrastructure Engineer").company("c-stripe", "Stripe")
                .applied("2026-08-10").status(ApplicationStatus.REJECTED).build());

        mongo.save(Fixtures.application("Site Reliability Engineer").company("c-datadog", "Datadog")
                .applied("2026-07-15").notes("C++ (senior) track. Heavy on-call.").build());

        mongo.save(Fixtures.application("Research Engineer").company("c-anthropic", "Anthropic")
                .applied("2026-08-20").build());
    }

    private Page<ApplicationSummaryResponse> search(String q) {
        return service.search(new ApplicationSearchRequest(q, null, null, null, null), UNSORTED);
    }

    // ------------------------------------------------- why this is a regex

    @Test
    @DisplayName("a partial word matches — the whole reason this is not a $text index")
    void partialWordMatches() {
        // "strip" is not a token and does not stem to "stripe"; $text would return nothing
        // here, which would make the filter bar feel broken as you type (SCHEMA.md §6).
        assertThat(search("strip")).extracting(ApplicationSummaryResponse::companyName)
                .containsOnly("Stripe");
        assertThat(search("strip")).hasSize(2);

        assertThat(search("eng")).hasSize(4);  // every role contains "Engineer"
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void caseInsensitive() {
        assertThat(search("STRIPE")).hasSize(2);
        assertThat(search("stripe")).hasSize(2);
    }

    @Test
    @DisplayName("searches role and notes, not only the company name")
    void searchesAllThreeFields() {
        assertThat(search("Payments")).extracting(ApplicationSummaryResponse::role)
                .containsExactly("Backend Engineer, Payments");
        assertThat(search("ledger")).hasSize(1);      // notes only
        assertThat(search("on-call")).hasSize(1);     // notes only
    }

    // -------------------------------------------------- why it is escaped

    @Test
    @DisplayName("an unbalanced bracket is a search term, not a 500")
    void metacharactersDoNotBlowUp() {
        // Unescaped, "(" is an unterminated group and Mongo rejects the pattern outright.
        // Someone typing a company name with a bracket in it would hit this on the first
        // keystroke of the bracket.
        assertThatCode(() -> search("(")).doesNotThrowAnyException();
        assertThat(search("(")).extracting(ApplicationSummaryResponse::companyName)
                .containsExactly("Datadog");         // "C++ (senior)"
        assertThat(search("(senior)")).hasSize(1);
        assertThat(search("C++")).hasSize(1);        // "+" unescaped is a quantifier
    }

    @Test
    @DisplayName("wildcards are literal text, so a pattern cannot match everything")
    void patternsAreLiteral() {
        // Unescaped, ".*" matches every document. Escaped, it matches the documents that
        // literally contain ".*" — none.
        assertThat(search(".*")).isEmpty();
    }

    @Test
    @DisplayName("blank free text is ignored rather than matching nothing")
    void blankQueryIgnored() {
        assertThat(search("   ")).hasSize(4);
        assertThat(search(null)).hasSize(4);
    }

    // ------------------------------------------------------ other filters

    @Test
    @DisplayName("status, company and date filters combine with free text")
    void filtersCombine() {
        assertThat(service.search(
                new ApplicationSearchRequest(null, ApplicationStatus.ACTIVE, null, null, null), UNSORTED))
                .hasSize(3);

        assertThat(service.search(
                new ApplicationSearchRequest(null, null, "c-stripe", null, null), UNSORTED))
                .hasSize(2);

        // Both Stripe roles, narrowed to the one that is still alive.
        assertThat(service.search(
                new ApplicationSearchRequest("stripe", ApplicationStatus.ACTIVE, null, null, null), UNSORTED))
                .extracting(ApplicationSummaryResponse::role)
                .containsExactly("Backend Engineer, Payments");
    }

    @Test
    @DisplayName("the appliedDate range is inclusive at both ends")
    void dateRangeInclusive() {
        Page<ApplicationSummaryResponse> august = service.search(
                new ApplicationSearchRequest(null, null, null,
                        LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 20)),
                UNSORTED);

        assertThat(august).hasSize(3);   // 08-02 and 08-20 both land inside; 07-15 does not
    }

    @Test
    @DisplayName("an inverted date range is rejected")
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> service.search(
                new ApplicationSearchRequest(null, null, null,
                        LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 2)),
                UNSORTED))
                .isInstanceOf(BadRequestException.class);
    }

    // -------------------------------------------------- paging and sorting

    @Test
    @DisplayName("defaults to newest first when the caller supplies no sort")
    void defaultSort() {
        assertThat(search(null)).extracting(ApplicationSummaryResponse::appliedDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("an explicit sort wins over the default")
    void explicitSort() {
        Page<ApplicationSummaryResponse> oldestFirst = service.search(
                new ApplicationSearchRequest(null, null, null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "appliedDate")));

        assertThat(oldestFirst).first()
                .extracting(ApplicationSummaryResponse::appliedDate)
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("the total spans the whole result set, not the page")
    void pagingTotals() {
        Page<ApplicationSummaryResponse> firstPage = service.search(
                new ApplicationSearchRequest(null, null, null, null, null), PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        Page<ApplicationSummaryResponse> secondPage = service.search(
                new ApplicationSearchRequest(null, null, null, null, null), PageRequest.of(1, 2));

        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).doesNotContainAnyElementsOf(firstPage.getContent());
    }
}
