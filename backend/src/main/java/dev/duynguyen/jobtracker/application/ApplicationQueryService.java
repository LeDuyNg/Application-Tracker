package dev.duynguyen.jobtracker.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import dev.duynguyen.jobtracker.application.dto.ApplicationSearchRequest;
import dev.duynguyen.jobtracker.application.dto.ApplicationSummaryResponse;
import dev.duynguyen.jobtracker.application.dto.FollowupResponse;
import dev.duynguyen.jobtracker.application.dto.UpcomingInterviewResponse;
import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageStatus;

/**
 * The three read features that are not stats: free-text search, follow-ups, and upcoming
 * interviews (SCHEMA.md §10.2–§10.4).
 *
 * <p><strong>Why these are not on {@code ApplicationService}.</strong> That class owns the
 * write path and the four derived fields — the invariants that are easy to break and where
 * every line is load-bearing. These three are pure reads against {@code MongoTemplate} that
 * mutate nothing. Keeping them apart stops the class holding the tricky rules from growing
 * a second, unrelated job, and mirrors {@code StatsService}, which is already split out for
 * the same reason.
 *
 * <p>All three share one thing: a relative time boundary. Every one of them resolves that
 * boundary through {@link TimeService} in the owner's timezone, never against UTC "now"
 * (SCHEMA.md §7).
 */
@Service
public class ApplicationQueryService {

    /** Follow-ups look a week ahead, so a reminder is visible before the day it lands. */
    static final int DUE_WITHIN_DAYS = 7;

    /** "Haven't heard back in 2+ weeks" — the threshold the headline query is named after. */
    static final int QUIET_AFTER_DAYS = 14;

    /** Guards against a caller asking for an interview window measured in centuries. */
    private static final int MAX_INTERVIEW_WINDOW_DAYS = 365;

    private final MongoTemplate mongo;
    private final ApplicationMapper mapper;
    private final TimeService time;

    ApplicationQueryService(MongoTemplate mongo, ApplicationMapper mapper, TimeService time) {
        this.mongo = mongo;
        this.mapper = mapper;
        this.time = time;
    }

    // -------------------------------------------------------------- Search

    /**
     * Filtered, paged application list (SCHEMA.md §10.3).
     *
     * <p><strong>Free text is a case-insensitive regex, not {@code $text}</strong>, and that
     * is a decision rather than an oversight. {@code $text} matches whole stemmed tokens, so
     * typing {@code strip} in the filter bar would not find Stripe and {@code eng} would not
     * find Engineer — which is precisely the interaction an as-you-type filter needs. At a
     * few hundred documents the scan is sub-millisecond, and skipping the text index leaves
     * the one-per-collection slot free (SCHEMA.md §6).
     *
     * <p><strong>The input is escaped before it reaches Mongo.</strong> A raw {@code (} is an
     * unbalanced group and comes back as a 500; a pathological pattern is a cheap way to
     * burn server CPU from the search box. {@link Pattern#quote} wraps the whole thing in
     * {@code \Q...\E}, which Mongo's PCRE engine honours, so every character is a literal.
     */
    public Page<ApplicationSummaryResponse> search(ApplicationSearchRequest request, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();

        if (request.q() != null && !request.q().isBlank()) {
            String literal = Pattern.quote(request.q().trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("companyName").regex(literal, "i"),
                    Criteria.where("role").regex(literal, "i"),
                    Criteria.where("notes").regex(literal, "i")));
        }
        if (request.status() != null) {
            filters.add(Criteria.where("status").is(request.status()));
        }
        if (request.companyId() != null && !request.companyId().isBlank()) {
            filters.add(Criteria.where("companyId").is(request.companyId()));
        }
        if (request.from() != null && request.to() != null && request.to().isBefore(request.from())) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        if (request.from() != null || request.to() != null) {
            // One chained Criteria rather than two: Query.addCriteria rejects the same key
            // twice, so gte and lte have to arrive together.
            Criteria dateRange = Criteria.where("appliedDate");
            if (request.from() != null) {
                dateRange = dateRange.gte(request.from());
            }
            if (request.to() != null) {
                dateRange = dateRange.lte(request.to());
            }
            filters.add(dateRange);
        }

        Query query = new Query();
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }

        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(), defaultSort());

        // Snapshot the filter before paging is applied — the count must span the whole
        // result set, not the page. Query.of copies; query.with(pageable) mutates in place.
        Query countQuery = Query.of(query);
        List<Application> page = mongo.find(query.with(effective), Application.class);

        // Skips the count query entirely when the result obviously fits on one page.
        return PageableExecutionUtils.getPage(
                page.stream().map(mapper::toSummary).toList(),
                effective,
                () -> mongo.count(countQuery, Application.class));
    }

    /** Newest first — the dashboard's default and the only ordering anyone asks for unprompted. */
    static Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "appliedDate");
    }

    // ----------------------------------------------------------- Follow-ups

    /**
     * Two queries, unioned in Java and returned as two labelled groups (SCHEMA.md §10.2).
     *
     * <p>They stay separate rather than becoming one {@code $or} because the caller needs to
     * know <em>why</em> each row is here: "you said to chase this today" and "this has gone
     * silent for two weeks" prompt different actions, and the MCP tool renders them under
     * different headings.
     *
     * <p>The gone-quiet half reads {@code lastContactAt} and <strong>never
     * {@code updatedAt}</strong>. Auditing bumps {@code updatedAt} on every write, so fixing
     * a typo in the notes would mark a dead pipeline as freshly active and hide it from the
     * one query that exists to surface it (SCHEMA.md §1).
     */
    public FollowupResponse followups() {
        Query due = Query.query(new Criteria().andOperator(
                        // A null followUpDate is not <= anything, so applications with no
                        // reminder set drop out here without needing an exists clause.
                        Criteria.where("followUpDate").lte(time.todayPlusDays(DUE_WITHIN_DAYS)),
                        Criteria.where("status").nin(ApplicationStatus.terminal())))
                .with(Sort.by(Sort.Direction.ASC, "followUpDate"));

        Query quiet = Query.query(new Criteria().andOperator(
                        // ACTIVE only, not merely non-terminal: an OFFER you are sitting on
                        // is not something the company has gone quiet about.
                        Criteria.where("status").is(ApplicationStatus.ACTIVE),
                        Criteria.where("lastContactAt").lte(time.nowMinusDays(QUIET_AFTER_DAYS))))
                .with(Sort.by(Sort.Direction.ASC, "lastContactAt"));

        Instant now = time.now();

        List<FollowupResponse.Due> dueItems = mongo.find(due, Application.class).stream()
                .map(a -> new FollowupResponse.Due(
                        mapper.toSummary(a),
                        ChronoUnit.DAYS.between(a.getFollowUpDate(), time.today())))
                .toList();

        List<FollowupResponse.GoneQuiet> quietItems = mongo.find(quiet, Application.class).stream()
                .map(a -> new FollowupResponse.GoneQuiet(
                        mapper.toSummary(a),
                        ChronoUnit.DAYS.between(a.getLastContactAt(), now)))
                .toList();

        return new FollowupResponse(dueItems, quietItems, DUE_WITHIN_DAYS, QUIET_AFTER_DAYS);
    }

    // -------------------------------------------------- Upcoming interviews

    /**
     * Rounds scheduled between now and {@code days} from now, soonest first (SCHEMA.md §10.4).
     *
     * <p><strong>Why the same date range is matched twice.</strong> Before the
     * {@code $unwind}, {@code stages.scheduledAt} is an array, and Mongo satisfies a range on
     * an array if <em>some</em> element clears the lower bound and <em>some</em> element
     * clears the upper — not necessarily the same one. So an application with an interview
     * last month and another next year would pass. That first match is only a cheap
     * index-backed prefilter to avoid unwinding the whole collection; the match after the
     * unwind is the one that is actually correct, because by then each stage stands alone.
     *
     * <p>Terminal applications are excluded. A round left sitting at {@code SCHEDULED} on an
     * application you withdrew from is stale data, not an appointment, and putting it on the
     * calendar view would be worse than useless.
     */
    public List<UpcomingInterviewResponse> upcomingInterviews(int days) {
        if (days <= 0) {
            throw new BadRequestException("'days' must be positive");
        }
        if (days > MAX_INTERVIEW_WINDOW_DAYS) {
            throw new BadRequestException("'days' must not exceed " + MAX_INTERVIEW_WINDOW_DAYS);
        }

        Instant now = time.now();
        Instant until = time.nowPlusDays(days);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(
                        Criteria.where("stages.scheduledAt").gte(now).lte(until),
                        Criteria.where("status").nin(ApplicationStatus.terminal()))),
                Aggregation.unwind("stages"),
                Aggregation.match(new Criteria().andOperator(
                        Criteria.where("stages.scheduledAt").gte(now).lte(until),
                        Criteria.where("stages.status").is(StageStatus.SCHEDULED))),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "stages.scheduledAt")),
                Aggregation.project("companyId", "companyName", "role")
                        .and("_id").as("applicationId")
                        .and("stages.stageId").as("stageId")
                        .and("stages.type").as("stageType")
                        .and("stages.scheduledAt").as("scheduledAt")
                        .and("stages.format").as("format")
                        .and("stages.interviewers").as("interviewers")
                        // Renaming _id is not enough — without this the original comes back
                        // too and the record has no field to receive it.
                        .andExclude("_id"));

        return mongo.aggregate(aggregation, "applications", UpcomingInterviewResponse.class)
                .getMappedResults();
    }
}
