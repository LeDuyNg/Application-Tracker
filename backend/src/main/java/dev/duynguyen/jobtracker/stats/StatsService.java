package dev.duynguyen.jobtracker.stats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import dev.duynguyen.jobtracker.common.BadRequestException;
import dev.duynguyen.jobtracker.common.TimeService;
import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;
import dev.duynguyen.jobtracker.common.enums.StageStatus;
import dev.duynguyen.jobtracker.common.enums.StageType;
import dev.duynguyen.jobtracker.stats.dto.StatsResponse;

/**
 * The stats aggregation (SCHEMA.md §9, §10.1).
 *
 * <p>Everything here is computed, never stored. One {@code $facet} runs the independent
 * counting pipelines in a single round trip; the rates are divided out in Java, because
 * arithmetic is clearer there than in a pipeline and the counts are what Mongo is good at.
 *
 * <p><strong>Why an aggregation rather than loading the documents and counting in Java.</strong>
 * At a personal scale — hundreds of applications — either works and Java would be simpler.
 * The pipeline is the right shape as data grows (the work happens next to the data, and only
 * the counts cross the wire), and this is the one place in the project where a real
 * aggregation is warranted. It is also, deliberately, the MongoDB talking point.
 */
@Service
public class StatsService {

    /** An application is "old enough to be ghosted" after this long with no reply (SCHEMA.md §9). */
    private static final int GHOST_THRESHOLD_DAYS = 21;

    private final MongoTemplate mongo;
    private final TimeService time;

    StatsService(MongoTemplate mongo, TimeService time) {
        this.mongo = mongo;
        this.time = time;
    }

    /**
     * @param days rolling window, mutually exclusive with from/to
     * @param from inclusive start of an explicit window
     * @param to   inclusive end of an explicit window
     */
    public StatsResponse compute(Integer days, LocalDate from, LocalDate to) {
        StatsResponse.Window window = resolveWindow(days, from, to);
        LocalDate ghostCutoff = time.today().minusDays(GHOST_THRESHOLD_DAYS);

        List<Criteria> windowCriteria = new ArrayList<>();
        if (window.from() != null) {
            windowCriteria.add(Criteria.where("appliedDate").gte(window.from()));
        }
        if (window.to() != null) {
            windowCriteria.add(Criteria.where("appliedDate").lte(window.to()));
        }
        MatchOperation matchWindow = Aggregation.match(
                windowCriteria.isEmpty() ? new Criteria() : new Criteria().andOperator(windowCriteria));

        Document result = runFacet(matchWindow, ghostCutoff);

        long total = count(result, "total");
        long withReply = count(result, "withReply");
        long offers = count(result, "offers");
        long active = count(result, "active");
        long ghosted = count(result, "ghosted");
        long ghostEligible = count(result, "ghostEligible");

        return new StatsResponse(
                window,
                total,
                byStatus(result),
                funnel(result),
                active,
                percentage(withReply, total),
                // Denominator is applications old enough to be ghosted, not all of them —
                // one sent last week cannot yet be ghosted, and including it would
                // understate the rate (SCHEMA.md §9).
                percentage(ghosted, ghostEligible),
                percentage(offers, total),
                avgDaysToFirstResponse(matchWindow));
    }

    private Document runFacet(MatchOperation matchWindow, LocalDate ghostCutoff) {
        FacetOperation facet = Aggregation.facet(Aggregation.count().as("value")).as("total")

                .and(Aggregation.group("status").count().as("value")).as("byStatus")

                // "stages.1 exists" is the cheap way to say "has at least 2 elements" —
                // array index 1 is the second entry. Anything past the initial submission
                // counts as a response (SCHEMA.md §9).
                .and(Aggregation.match(Criteria.where("stages.1").exists(true)),
                        Aggregation.count().as("value")).as("withReply")

                .and(Aggregation.match(Criteria.where("status")
                                .in(ApplicationStatus.OFFER, ApplicationStatus.ACCEPTED)),
                        Aggregation.count().as("value")).as("offers")

                .and(Aggregation.match(Criteria.where("status").is(ApplicationStatus.ACTIVE)),
                        Aggregation.count().as("value")).as("active")

                .and(Aggregation.match(new Criteria().andOperator(
                                Criteria.where("status").in(ApplicationStatus.ACTIVE, ApplicationStatus.GHOSTED),
                                Criteria.where("stages.1").exists(false),
                                Criteria.where("appliedDate").lte(ghostCutoff))),
                        Aggregation.count().as("value")).as("ghosted")

                .and(Aggregation.match(Criteria.where("appliedDate").lte(ghostCutoff)),
                        Aggregation.count().as("value")).as("ghostEligible")

                // Funnel: per application, the furthest stage it actually passed. Sorting by
                // sequence before grouping is what makes $last mean "highest sequence" —
                // without the sort, $last is document order, which is not guaranteed.
                .and(Aggregation.unwind("stages"),
                        Aggregation.match(Criteria.where("stages.status").is(StageStatus.PASSED)),
                        Aggregation.sort(org.springframework.data.domain.Sort.by("_id", "stages.sequence")),
                        Aggregation.group("_id").last("stages.type").as("furthest"),
                        Aggregation.group("furthest").count().as("value")).as("funnel");

        AggregationResults<Document> results = mongo.aggregate(
                Aggregation.newAggregation(matchWindow, facet), "applications", Document.class);
        Document doc = results.getUniqueMappedResult();
        return doc == null ? new Document() : doc;
    }

    /**
     * Mean days from applying to the first sign of life.
     *
     * <p>A separate round trip rather than another {@code $facet} branch: it needs an
     * {@code $unwind} plus two {@code $group}s plus date arithmetic, and folding that into
     * the facet made the whole pipeline hard to read for one number. Two queries at this
     * scale cost nothing.
     *
     * <p>Takes the earliest of {@code completedAt}/{@code scheduledAt} across all stages
     * after the first, which is equivalent to SCHEMA.md §9's "lowest sequence greater than
     * 1" whenever dates advance with sequence, and more robust when they do not.
     */
    private Double avgDaysToFirstResponse(MatchOperation matchWindow) {
        Aggregation aggregation = Aggregation.newAggregation(
                matchWindow,
                Aggregation.unwind("stages"),
                Aggregation.match(Criteria.where("stages.sequence").gt(1)),
                Aggregation.project("appliedDate")
                        .and(org.springframework.data.mongodb.core.aggregation.ConditionalOperators
                                .ifNull("stages.completedAt").thenValueOf("stages.scheduledAt"))
                        .as("respondedAt"),
                Aggregation.match(Criteria.where("respondedAt").ne(null)),
                Aggregation.group("_id")
                        .min("respondedAt").as("firstAt")
                        .first("appliedDate").as("appliedDate"),
                Aggregation.project()
                        .andExpression("(firstAt - appliedDate) / 86400000").as("days"),
                Aggregation.group().avg("days").as("value"));

        Document doc = mongo.aggregate(aggregation, "applications", Document.class).getUniqueMappedResult();
        if (doc == null || doc.get("value") == null) {
            return null;
        }
        return round(((Number) doc.get("value")).doubleValue());
    }

    private StatsResponse.Window resolveWindow(Integer days, LocalDate from, LocalDate to) {
        if (days != null && (from != null || to != null)) {
            throw new BadRequestException("Use either 'days' or 'from'/'to', not both");
        }
        if (days != null) {
            if (days <= 0) {
                throw new BadRequestException("'days' must be positive");
            }
            LocalDate start = time.today().minusDays(days);
            return new StatsResponse.Window(start, time.today(), "the last %d days".formatted(days));
        }
        if (from != null || to != null) {
            if (from != null && to != null && to.isBefore(from)) {
                throw new BadRequestException("'to' must not be before 'from'");
            }
            String description = from != null && to != null
                    ? "%s to %s".formatted(from, to)
                    : from != null ? "since %s".formatted(from) : "up to %s".formatted(to);
            return new StatsResponse.Window(from, to, description);
        }
        return new StatsResponse.Window(null, null, "all time");
    }

    // ---------------------------------------------------------- result parsing

    @SuppressWarnings("unchecked")
    private List<Document> branch(Document result, String name) {
        Object value = result.get(name);
        return value instanceof List<?> list ? (List<Document>) list : List.of();
    }

    /** A {@code $count} branch yields either one document or, when nothing matched, none. */
    private long count(Document result, String name) {
        List<Document> branch = branch(result, name);
        return branch.isEmpty() ? 0L : ((Number) branch.getFirst().get("value")).longValue();
    }

    private Map<ApplicationStatus, Long> byStatus(Document result) {
        Map<ApplicationStatus, Long> counts = new LinkedHashMap<>();
        for (Document d : branch(result, "byStatus")) {
            Object id = d.get("_id");
            if (id != null) {
                counts.put(ApplicationStatus.valueOf(id.toString()), ((Number) d.get("value")).longValue());
            }
        }
        return counts;
    }

    /** Ordered by {@code StageType} declaration order, which is the pipeline's progression. */
    private List<StatsResponse.FunnelEntry> funnel(Document result) {
        List<StatsResponse.FunnelEntry> entries = new ArrayList<>();
        for (Document d : branch(result, "funnel")) {
            Object id = d.get("_id");
            if (id != null) {
                entries.add(new StatsResponse.FunnelEntry(
                        StageType.valueOf(id.toString()), ((Number) d.get("value")).longValue()));
            }
        }
        entries.sort(Comparator.comparing(e -> e.stageType().ordinal()));
        return entries;
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : round(numerator * 100.0 / denominator);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
