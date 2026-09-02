package dev.duynguyen.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * Every application endpoint over real HTTP against a real MongoDB.
 *
 * <p>This is the layer where the pieces meet: binding, Bean Validation, the services'
 * exceptions, and {@code GlobalExceptionHandler}'s mapping of them to status codes. Each
 * endpoint gets its happy path and its primary failure, per the cross-cutting checklist in
 * {@code PLAN.md}.
 *
 * <p>Unlike the query ITs, this one runs on the <strong>real clock</strong> — it exercises
 * the wiring, and pinning time would mean replacing the context's {@code TimeService}. Dates
 * are therefore relative to {@code now()} rather than hardcoded.
 */
@AutoConfigureMockMvc
class ApplicationControllerIT extends AbstractMongoIT {

    @Autowired private MockMvc mvc;
    @Autowired private MongoTemplate mongo;

    private String companyId;

    @BeforeEach
    void setUp() throws Exception {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());
        mongo.getCollection("companies").deleteMany(new org.bson.Document());
        companyId = createCompany("Stripe");
    }

    // ------------------------------------------------------------ fixtures

    private String createCompany(String name) throws Exception {
        String body = mvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** An application with only the required fields — the service seeds its first stage. */
    private String createApplication(String role) throws Exception {
        String body = mvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "role": "%s",
                                  "appliedDate": "%s",
                                  "source": "REFERRAL"
                                }""".formatted(companyId, role, LocalDate.now().minusDays(10))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // ---------------------------------------------------------------- CRUD

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("201 with a Location header, ACTIVE by default, and a seeded submission stage")
        void createsWithDefaults() throws Exception {
            mvc.perform(post("/api/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "companyId": "%s",
                                      "role": "Backend Engineer",
                                      "appliedDate": "%s",
                                      "source": "REFERRAL"
                                    }""".formatted(companyId, LocalDate.now().minusDays(3))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/applications/")))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.companyName").value("Stripe"))
                    // Neither status nor stages was sent; both are the service's job.
                    .andExpect(jsonPath("$.stages.length()").value(1))
                    .andExpect(jsonPath("$.stages[0].type").value("APPLICATION_SUBMITTED"))
                    .andExpect(jsonPath("$.stages[0].sequence").value(1))
                    .andExpect(jsonPath("$.lastContactAt").isNotEmpty());
        }

        @Test
        @DisplayName("400 naming every invalid field, not just the first")
        void reportsAllValidationErrors() throws Exception {
            mvc.perform(post("/api/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "companyId": "", "role": "" }"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    // companyId, role, appliedDate and source are all bad or missing.
                    .andExpect(jsonPath("$.errors.length()").value(4));
        }

        @Test
        @DisplayName("400 when companyId does not exist")
        void rejectsUnknownCompany() throws Exception {
            mvc.perform(post("/api/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "companyId": "000000000000000000000000",
                                      "role": "Backend Engineer",
                                      "appliedDate": "%s",
                                      "source": "REFERRAL"
                                    }""".formatted(LocalDate.now().minusDays(1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not exist")));
        }

        @Test
        @DisplayName("400 listing the legal values when an enum constant is unknown")
        void rejectsUnknownEnumValue() throws Exception {
            mvc.perform(post("/api/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "companyId": "%s",
                                      "role": "Backend Engineer",
                                      "appliedDate": "%s",
                                      "source": "CARRIER_PIGEON"
                                    }""".formatted(companyId, LocalDate.now().minusDays(1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed Request"))
                    // The passed-through Jackson message names every accepted constant,
                    // which is the whole reason it is passed through.
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("REFERRAL")));
        }

        @Test
        @DisplayName("400 when appliedDate is in the future")
        void rejectsFutureAppliedDate() throws Exception {
            mvc.perform(post("/api/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "companyId": "%s",
                                      "role": "Backend Engineer",
                                      "appliedDate": "%s",
                                      "source": "REFERRAL"
                                    }""".formatted(companyId, LocalDate.now().plusDays(30))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("GET returns the application; an unknown id is 404, not an empty 200")
    void getAndNotFound() throws Exception {
        String id = createApplication("Backend Engineer");

        mvc.perform(get("/api/applications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Backend Engineer"));

        mvc.perform(get("/api/applications/{id}", "000000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT replaces the application's own fields and can set a terminal status")
    void update() throws Exception {
        String id = createApplication("Backend Engineer");

        mvc.perform(put("/api/applications/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "role": "Backend Engineer, Payments",
                                  "status": "WITHDRAWN",
                                  "appliedDate": "%s",
                                  "source": "COLD_APPLY",
                                  "notes": "Pulled out — took another offer."
                                }""".formatted(companyId, LocalDate.now().minusDays(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Backend Engineer, Payments"))
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.source").value("COLD_APPLY"));
    }

    @Test
    @DisplayName("DELETE is 204 and the application is really gone")
    void deleteApplication() throws Exception {
        String id = createApplication("Backend Engineer");

        mvc.perform(delete("/api/applications/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/applications/{id}", id)).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------- stages

    @Nested
    @DisplayName("stages sub-resource")
    class Stages {

        @Test
        @DisplayName("POST appends a round and returns the whole updated application")
        void addStage() throws Exception {
            String id = createApplication("Backend Engineer");

            String body = mvc.perform(post("/api/applications/{id}/stages", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "type": "TECHNICAL_INTERVIEW",
                                      "status": "SCHEDULED",
                                      "scheduledAt": "%s",
                                      "format": "VIDEO",
                                      "interviewers": ["Priya N. (Staff Eng)"]
                                    }""".formatted(Instant.now().plus(3, ChronoUnit.DAYS))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stages.length()").value(2))
                    .andExpect(jsonPath("$.stages[1].sequence").value(2))
                    // The parent's derived fields moved too, which is why the whole
                    // application comes back rather than just the stage.
                    .andExpect(jsonPath("$.currentStageType").value("TECHNICAL_INTERVIEW"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(JsonPath.<String>read(body, "$.stages[1].stageId")).isNotBlank();
        }

        @Test
        @DisplayName("400 when SCHEDULED arrives without a scheduledAt")
        void rejectsScheduledWithoutDate() throws Exception {
            String id = createApplication("Backend Engineer");

            mvc.perform(post("/api/applications/{id}/stages", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "type": "TECHNICAL_INTERVIEW", "status": "SCHEDULED" }"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("scheduledAt is required")));
        }

        @Test
        @DisplayName("PATCH replaces a round's fields; an unknown stageId is 404")
        void updateStage() throws Exception {
            String id = createApplication("Backend Engineer");
            String body = mvc.perform(get("/api/applications/{id}", id))
                    .andReturn().getResponse().getContentAsString();
            String stageId = JsonPath.read(body, "$.stages[0].stageId");

            mvc.perform(patch("/api/applications/{id}/stages/{stageId}", id, stageId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "type": "APPLICATION_SUBMITTED",
                                      "status": "PASSED",
                                      "completedAt": "%s",
                                      "notes": "Applied through the referral portal"
                                    }""".formatted(Instant.now().minus(10, ChronoUnit.DAYS))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stages[0].notes").value("Applied through the referral portal"));

            mvc.perform(patch("/api/applications/{id}/stages/{stageId}", id, "no-such-stage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "type": "OFFER", "status": "EXPECTED" }"""))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("DELETE removes a round, but refuses to remove the last one")
        void deleteStage() throws Exception {
            String id = createApplication("Backend Engineer");
            mvc.perform(post("/api/applications/{id}/stages", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "type": "RECRUITER_SCREEN", "status": "EXPECTED" }"""))
                    .andExpect(status().isCreated());

            String body = mvc.perform(get("/api/applications/{id}", id))
                    .andReturn().getResponse().getContentAsString();
            String second = JsonPath.read(body, "$.stages[1].stageId");
            String first = JsonPath.read(body, "$.stages[0].stageId");

            mvc.perform(delete("/api/applications/{id}/stages/{stageId}", id, second))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stages.length()").value(1));

            // An application with no stages could never appear in the funnel or the
            // response-rate denominator, so the service refuses rather than allowing it.
            mvc.perform(delete("/api/applications/{id}/stages/{stageId}", id, first))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------- read features

    @Test
    @DisplayName("search returns a stable PagedModel envelope")
    void searchEnvelope() throws Exception {
        createApplication("Backend Engineer");
        createApplication("Infrastructure Engineer");

        mvc.perform(get("/api/applications").param("q", "engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("search honours filters and rejects an unknown status")
    void searchFilters() throws Exception {
        createApplication("Backend Engineer");

        mvc.perform(get("/api/applications").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mvc.perform(get("/api/applications").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("NOT_A_STATUS")));
    }

    @Test
    @DisplayName("followups returns both groups with their thresholds")
    void followups() throws Exception {
        String id = createApplication("Backend Engineer");
        mvc.perform(put("/api/applications/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "companyId": "%s",
                          "role": "Backend Engineer",
                          "status": "ACTIVE",
                          "appliedDate": "%s",
                          "source": "REFERRAL",
                          "followUpDate": "%s"
                        }""".formatted(companyId, LocalDate.now().minusDays(10), LocalDate.now())));

        mvc.perform(get("/api/applications/followups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.due.length()").value(1))
                .andExpect(jsonPath("$.due[0].daysOverdue").value(0))
                .andExpect(jsonPath("$.dueWithinDays").value(7))
                .andExpect(jsonPath("$.quietAfterDays").value(14))
                .andExpect(jsonPath("$.goneQuiet").isArray());
    }

    @Test
    @DisplayName("interviews defaults to a week and rejects a non-positive window")
    void upcomingInterviews() throws Exception {
        String id = createApplication("Backend Engineer");
        mvc.perform(post("/api/applications/{id}/stages", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "type": "TECHNICAL_INTERVIEW",
                          "status": "SCHEDULED",
                          "scheduledAt": "%s"
                        }""".formatted(Instant.now().plus(2, ChronoUnit.DAYS))));

        mvc.perform(get("/api/applications/interviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stageType").value("TECHNICAL_INTERVIEW"))
                .andExpect(jsonPath("$[0].companyName").value("Stripe"));

        // One day is too narrow to reach an interview two days out.
        mvc.perform(get("/api/applications/interviews").param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/applications/interviews").param("days", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the literal routes win over /{id} regardless of declaration order")
    void literalRoutesAreNotShadowed() throws Exception {
        // If PathPattern ever scored these as ids, both would 404 through the service.
        mvc.perform(get("/api/applications/followups")).andExpect(status().isOk());
        mvc.perform(get("/api/applications/interviews")).andExpect(status().isOk());
    }
}
