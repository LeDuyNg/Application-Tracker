package dev.duynguyen.jobtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;

/**
 * The Phase 5 metrics: that the domain counters move, and that the cardinality budget holds.
 *
 * <p><strong>Every assertion here is a delta, never an absolute count.</strong> Meters live
 * in the application context, Spring caches that context across the whole IT run, and
 * counters are monotonic — so the value of {@code jobtracker.applications.created} when this
 * class runs depends entirely on which other classes ran first. Asserting {@code == 1} would
 * pass or fail on Maven's {@code runOrder}, which is precisely the failure that cost a CI-only
 * red build already (CLAUDE.md §6, "A test that passed locally and failed in CI"). Reading
 * before and after and asserting on the difference is immune to it.
 */
@AutoConfigureMockMvc
@WithMockUser
class MetricsIT extends AbstractMongoIT {

    @Autowired private MockMvc mvc;
    @Autowired private MongoTemplate mongo;
    @Autowired private MeterRegistry metrics;
    @Autowired private TimeService time;

    @BeforeEach
    void setUp() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());
        mongo.getCollection("companies").deleteMany(new org.bson.Document());
    }

    /** 0 when the counter does not exist yet, which is the state on the very first call. */
    private double count(String name, String... tags) {
        Search search = metrics.find(name);
        for (int i = 0; i < tags.length; i += 2) {
            search = search.tag(tags[i], tags[i + 1]);
        }
        var counter = search.counter();
        return counter == null ? 0d : counter.count();
    }

    private String createCompany(String name) throws Exception {
        String body = mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createApplication(String companyId) throws Exception {
        String body = mvc.perform(post("/api/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "role": "Backend Engineer",
                                  "appliedDate": "%s",
                                  "source": "COLD_APPLY"
                                }""".formatted(companyId, time.today())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // ------------------------------------------------------- domain counters

    @Nested
    @DisplayName("domain counters")
    class DomainCounters {

        @Test
        @DisplayName("creating an application increments jobtracker.applications.created")
        void applicationsCreated() throws Exception {
            double before = count("jobtracker.applications.created");
            createApplication(createCompany("Stripe"));
            assertThat(count("jobtracker.applications.created")).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("a rejected create does not count")
        void rejectedCreateIsNotCounted() throws Exception {
            double before = count("jobtracker.applications.created");

            // companyId does not exist -> 400 from the service, before any save.
            mvc.perform(post("/api/applications").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "companyId": "000000000000000000000000",
                                      "role": "Ghost",
                                      "appliedDate": "%s",
                                      "source": "COLD_APPLY"
                                    }""".formatted(LocalDate.now())))
                    .andExpect(status().isBadRequest());

            assertThat(count("jobtracker.applications.created")).isEqualTo(before);
        }

        @Test
        @DisplayName("adding a stage increments jobtracker.stages.added, tagged by type")
        void stagesAddedIsTaggedByType() throws Exception {
            String appId = createApplication(createCompany("Datadog"));
            double before = count("jobtracker.stages.added", "stage_type", "TECHNICAL_INTERVIEW");

            mvc.perform(post("/api/applications/" + appId + "/stages").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "type": "TECHNICAL_INTERVIEW", "status": "EXPECTED" }"""))
                    .andExpect(status().isCreated());

            assertThat(count("jobtracker.stages.added", "stage_type", "TECHNICAL_INTERVIEW"))
                    .isEqualTo(before + 1);
        }

        @Test
        @DisplayName("the seeded APPLICATION_SUBMITTED stage is counted too")
        void createdApplicationCountsItsSeededStage() throws Exception {
            // create() seeds an APPLICATION_SUBMITTED stage without going through addStage().
            // Instrumenting only addStage() undercounted every application by one, and left
            // the dashboard's stage widget reading "no data" after a backfill.
            double before = count("jobtracker.stages.added", "stage_type", "APPLICATION_SUBMITTED");
            createApplication(createCompany("Vercel"));
            assertThat(count("jobtracker.stages.added", "stage_type", "APPLICATION_SUBMITTED"))
                    .isEqualTo(before + 1);
        }

        @Test
        @DisplayName("an application created with its stages supplied counts every one")
        void suppliedStagesAreAllCounted() throws Exception {
            // The backfill shape: stages arrive attached to the create, so create() takes its
            // buildStages() branch and never touches addStage(). This counted zero.
            String companyId = createCompany("Figma");
            double submitted = count("jobtracker.stages.added", "stage_type", "APPLICATION_SUBMITTED");
            double screen = count("jobtracker.stages.added", "stage_type", "RECRUITER_SCREEN");

            mvc.perform(post("/api/applications").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "companyId": "%s",
                                      "role": "Product Engineer",
                                      "appliedDate": "%s",
                                      "source": "REFERRAL",
                                      "stages": [
                                        { "type": "APPLICATION_SUBMITTED", "status": "PASSED",
                                          "completedAt": "2026-08-01T12:00:00Z" },
                                        { "type": "RECRUITER_SCREEN", "status": "PASSED",
                                          "completedAt": "2026-08-08T12:00:00Z" }
                                      ]
                                    }""".formatted(companyId, time.today())))
                    .andExpect(status().isCreated());

            assertThat(count("jobtracker.stages.added", "stage_type", "APPLICATION_SUBMITTED"))
                    .isEqualTo(submitted + 1);
            assertThat(count("jobtracker.stages.added", "stage_type", "RECRUITER_SCREEN"))
                    .isEqualTo(screen + 1);
        }

        @Test
        @DisplayName("an error response increments jobtracker.api.errors, tagged by status")
        void apiErrorsIsTaggedByStatus() throws Exception {
            double before = count("jobtracker.api.errors", "status", "404");

            mvc.perform(get("/api/applications/000000000000000000000000"))
                    .andExpect(status().isNotFound());

            assertThat(count("jobtracker.api.errors", "status", "404")).isEqualTo(before + 1);
        }
    }

    // --------------------------------------------------- the cardinality budget

    @Nested
    @DisplayName("custom-metric budget")
    class Budget {

        /**
         * Probes the filter directly rather than asserting on whichever Micrometer binders
         * happen to be auto-configured. Those change with dependencies; the rule does not.
         */
        @Test
        @DisplayName("a metric outside the allowlist is never registered")
        void unknownMetricsAreDenied() {
            metrics.counter("some.library.we.added.later").increment();
            assertThat(metrics.find("some.library.we.added.later").counter()).isNull();
        }

        @Test
        @DisplayName("our own metrics and the allowlisted vitals get through")
        void allowedMetricsSurvive() {
            metrics.counter("jobtracker.probe").increment();
            assertThat(metrics.find("jobtracker.probe").counter()).isNotNull();

            metrics.counter("http.server.requests", "uri", "/probe").increment();
            assertThat(metrics.find("http.server.requests").counter()).isNotNull();
        }

        @Test
        @DisplayName("outcome and exception are stripped from http.server.requests")
        void highCardinalityTagsAreStripped() {
            metrics.counter("http.server.requests",
                    "uri", "/api/applications",
                    "outcome", "CLIENT_ERROR",
                    "exception", "NotFoundException").increment();

            var counter = metrics.find("http.server.requests").tag("uri", "/api/applications").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.getId().getTag("outcome")).isNull();
            assertThat(counter.getId().getTag("exception")).isNull();
        }
    }
}
