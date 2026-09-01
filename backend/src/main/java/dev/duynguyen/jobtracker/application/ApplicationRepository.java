package dev.duynguyen.jobtracker.application;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import dev.duynguyen.jobtracker.common.enums.ApplicationStatus;

/**
 * Simple derived queries only.
 *
 * <p>Everything the four read features need — stats aggregation, upcoming interviews,
 * gone-quiet follow-ups, free-text search — traverses {@code stages[]}, groups, or filters
 * on several optional criteria at once. Those live in the services against
 * {@code MongoTemplate}, where the pipeline is explicit and reviewable, rather than being
 * encoded in a method name (CLAUDE.md §11, SCHEMA.md §10).
 */
public interface ApplicationRepository extends MongoRepository<Application, String> {

    List<Application> findByCompanyId(String companyId);

    List<Application> findByStatus(ApplicationStatus status);

    /** Used by CompanyService.delete to decide between deleting and returning 409. */
    long countByCompanyId(String companyId);

    /** Used when a company is renamed, to re-write the denormalized copy. */
    List<Application> findByCompanyIdOrderByAppliedDateDesc(String companyId);
}
