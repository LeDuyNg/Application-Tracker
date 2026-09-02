package dev.duynguyen.jobtracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import dev.duynguyen.jobtracker.application.Application;
import dev.duynguyen.jobtracker.company.Company;

/**
 * Creates every index in SCHEMA.md §6 at startup.
 *
 * <p><strong>Why a runner instead of {@code @Indexed} annotations.</strong> Spring Data can
 * create indexes from annotations, but only with
 * {@code spring.data.mongodb.auto-index-creation=true}, which is off by default since Boot
 * 3 — and for good reason: index creation then happens implicitly, scattered across entity
 * classes, on first use of each collection. Doing it here means the full set is in one
 * readable place, runs at a known moment, and is logged. On a production Atlas cluster you
 * want to <em>see</em> what indexes exist and why.
 *
 * <p>{@code ensureIndex} is idempotent: creating an index that already exists with the same
 * definition is a no-op, so this is safe on every boot. Changing a definition is not
 * automatic though — Mongo will reject a same-name-different-options index, and you have to
 * drop the old one by hand.
 *
 * <p><strong>There is deliberately no text index.</strong> {@code $text} matches whole
 * stemmed tokens, so "strip" would never find Stripe and "eng" would never find Engineer —
 * useless for the SPA's filter bar. Free-text search uses an escaped case-insensitive regex
 * instead (SCHEMA.md §6, §10.3).
 */
@Component
public class IndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    IndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureCompanyIndexes();
        ensureApplicationIndexes();
    }

    private void ensureCompanyIndexes() {
        IndexOperations ops = mongoTemplate.indexOps(Company.class);

        // One document per employer. This unique index is what actually enforces that —
        // the service's findByNameIgnoreCase check is a courtesy that races under
        // concurrent writes; the index is the guarantee.
        create(ops, "companies", new Index().on("name", Sort.Direction.ASC).unique().named("name_unique"));
    }

    private void ensureApplicationIndexes() {
        IndexOperations ops = mongoTemplate.indexOps(Application.class);

        // "All my applications at company X", and the companyName re-write on rename.
        create(ops, "applications", new Index().on("companyId", Sort.Direction.ASC).named("companyId"));

        // Status filter and the stats grouping.
        create(ops, "applications", new Index().on("status", Sort.Direction.ASC).named("status"));

        // Default list sort and every date-range filter. Descending matches the query.
        create(ops, "applications", new Index().on("appliedDate", Sort.Direction.DESC).named("appliedDate_desc"));

        // Sparse: followUpDate is optional, and a sparse index skips documents that lack it
        // entirely — smaller, and it matches the query, which only ever looks for documents
        // that have one.
        create(ops, "applications", new Index().on("followUpDate", Sort.Direction.ASC).sparse().named("followUpDate_sparse"));

        // Sparse for the same reason: only applications that have had contact carry this.
        create(ops, "applications", new Index().on("lastContactAt", Sort.Direction.ASC).sparse().named("lastContactAt_sparse"));

        // Multikey — Mongo indexes each array element. Powers get_upcoming_interviews.
        create(ops, "applications", new Index().on("stages.scheduledAt", Sort.Direction.ASC).named("stages_scheduledAt"));

        // Multikey: "applications with a round EXPECTED / SCHEDULED".
        create(ops, "applications", new Index().on("stages.status", Sort.Direction.ASC).named("stages_status"));

        // Grouping by company, and an anchored ^prefix regex can use this.
        create(ops, "applications", new Index().on("companyName", Sort.Direction.ASC).named("companyName"));

        // NOTE: SCHEMA.md §6 also lists a compound { status: 1, appliedDate: -1 } for the
        // dashboard default view. Deliberately not created — the single-field indexes above
        // cover it at this data volume, and SCHEMA says to add it only once measurement
        // shows they are insufficient.
    }

    private void create(IndexOperations ops, String collection, Index index) {
        String name = ops.createIndex(index);
        log.info("ensured index {}.{}", collection, name);
    }
}
