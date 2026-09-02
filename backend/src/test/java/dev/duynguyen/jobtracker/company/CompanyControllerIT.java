package dev.duynguyen.jobtracker.company;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.support.AbstractMongoIT;

/**
 * Company endpoints over real HTTP.
 *
 * <p>The two tests that matter are {@link #renameCascadesToApplications()} and
 * {@link #deleteIsBlockedWhileApplicationsReferenceIt()} — the rules that keep the
 * reference-plus-denormalized-copy modelling honest (SCHEMA.md §1, CLAUDE.md §6). Ordinary
 * CRUD is here for coverage; those two are here because breaking them corrupts data quietly.
 */
@AutoConfigureMockMvc
// Phase 2 gates every /api/** route behind a session. These tests are about the
// endpoints, not the login, so they run as an already-authenticated user; SecurityIT
// is where the gate itself is tested.
@WithMockUser
class CompanyControllerIT extends AbstractMongoIT {

    @Autowired private MockMvc mvc;
    @Autowired private MongoTemplate mongo;
    @Autowired private TimeService time;

    /**
     * Today <strong>in {@code app.timezone}</strong>, not in the JVM's default zone.
     *
     * <p>These are not interchangeable and the difference is invisible for most of the
     * day. This suite ran green for hours using {@code LocalDate.now()} and started
     * failing at 21:00 Pacific — the moment it became tomorrow in New York — with
     * {@code daysOverdue} off by exactly one. The app computes every relative date
     * boundary in the owner's timezone (SCHEMA.md §7); a test asserting on those
     * boundaries has to use the same clock or it is testing the laptop's location.
     */
    private LocalDate today() {
        return time.today();
    }

    @BeforeEach
    void setUp() {
        mongo.getCollection("applications").deleteMany(new org.bson.Document());
        mongo.getCollection("companies").deleteMany(new org.bson.Document());
    }

    private String create(String name) throws Exception {
        String body = mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createApplicationAt(String companyId, String role) throws Exception {
        String body = mvc.perform(post("/api/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "role": "%s",
                                  "appliedDate": "%s",
                                  "source": "COLD_APPLY"
                                }""".formatted(companyId, role, today().minusDays(5))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    @DisplayName("201 with a Location header, and contacts round-trip")
    void createWithContacts() throws Exception {
        mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stripe",
                                  "website": "https://stripe.com/jobs",
                                  "industry": "Fintech",
                                  "contacts": [
                                    { "name": "Alex Kim", "title": "Technical Recruiter",
                                      "email": "akim@example.com" }
                                  ],
                                  "tags": ["Fintech", "HIGH-BAR"]
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/companies/")))
                .andExpect(jsonPath("$.contacts[0].name").value("Alex Kim"))
                // Tags are lowercased on save so filtering never depends on how they were typed.
                .andExpect(jsonPath("$.tags[0]").value("fintech"))
                .andExpect(jsonPath("$.tags[1]").value("high-bar"));
    }

    @Test
    @DisplayName("400 on a blank name, and on a contact with a malformed email")
    void validation() throws Exception {
        mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "  " }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));

        // @Valid on the contacts list is what makes validation recurse into it — without
        // that annotation this case passes silently.
        mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Datadog",
                                  "contacts": [ { "name": "Sam", "email": "not-an-email" } ]
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(
                        org.hamcrest.Matchers.containsString("contacts[0].email")));
    }

    @Test
    @DisplayName("a website with a non-http scheme is rejected, http(s) and blank are accepted")
    void websiteSchemeIsRestricted() throws Exception {
        // The SPA renders this value straight into an <a href>, and React does not sanitise
        // href attributes — it escapes text content only. Without the scheme allowlist a
        // stored "javascript:..." is a link that runs script in the app's own origin.
        for (String hostile : new String[] {
                "javascript:alert(document.cookie)",
                "JaVaScRiPt:alert(1)",
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
                "vbscript:msgbox(1)" }) {
            mvc.perform(post("/api/companies").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "name": "Hostile %s", "website": "%s" }"""
                                    .formatted(hostile.hashCode(), hostile)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("website"));
        }

        mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Fine", "website": "https://stripe.com/jobs" }"""))
                .andExpect(status().isCreated());

        // Blank is "not filled in", not "malformed" — the SPA sends null, but the .http
        // collection and the MCP client need not.
        mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Blank site", "website": "" }"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("409 on a duplicate name, whatever the casing")
    void duplicateName() throws Exception {
        create("Stripe");

        mvc.perform(post("/api/companies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "stripe" }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    @DisplayName("list is alphabetical; an unknown id is 404")
    void listAndGet() throws Exception {
        create("Stripe");
        create("Anthropic");
        create("Datadog");

        mvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Anthropic"))
                .andExpect(jsonPath("$[2].name").value("Stripe"));

        mvc.perform(get("/api/companies/{id}", "000000000000000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("renaming rewrites the denormalized companyName on every application")
    void renameCascadesToApplications() throws Exception {
        String companyId = create("Facebook");
        String applicationId = createApplicationAt(companyId, "Backend Engineer");
        createApplicationAt(companyId, "Infrastructure Engineer");

        mvc.perform(put("/api/companies/{id}", companyId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Meta" }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Meta"));

        // Without the cascade the copy drifts and search stops finding renamed companies —
        // silently, because nothing errors.
        mvc.perform(get("/api/applications/{id}", applicationId))
                .andExpect(jsonPath("$.companyName").value("Meta"));

        mvc.perform(get("/api/applications").param("q", "Meta"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("delete is blocked with 409 while applications reference the company")
    void deleteIsBlockedWhileApplicationsReferenceIt() throws Exception {
        String companyId = create("Stripe");
        String applicationId = createApplicationAt(companyId, "Backend Engineer");

        // Cascading here would destroy interview history to satisfy one click, so the
        // service refuses and says how many are in the way.
        mvc.perform(delete("/api/companies/{id}", companyId).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 application")));

        mvc.perform(delete("/api/applications/{id}", applicationId).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/companies/{id}", companyId).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/companies/{id}", companyId)).andExpect(status().isNotFound());
    }
}
