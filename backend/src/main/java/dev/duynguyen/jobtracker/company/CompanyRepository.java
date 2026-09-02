package dev.duynguyen.jobtracker.company;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Derived queries only. Anything involving aggregation or array traversal goes through
 * {@code MongoTemplate} in a service instead (CLAUDE.md §11).
 */
public interface CompanyRepository extends MongoRepository<Company, String> {

    /**
     * Used to give a friendly 409 before attempting an insert that the unique index would
     * reject anyway. This check races under concurrent writes — the index is the real
     * guarantee, this is just a better error message.
     */
    Optional<Company> findByNameIgnoreCase(String name);
}
