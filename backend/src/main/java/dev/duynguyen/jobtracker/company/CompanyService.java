package dev.duynguyen.jobtracker.company;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import dev.duynguyen.jobtracker.application.Application;
import dev.duynguyen.jobtracker.application.ApplicationRepository;
import dev.duynguyen.jobtracker.common.ConflictException;
import dev.duynguyen.jobtracker.common.NotFoundException;
import dev.duynguyen.jobtracker.company.dto.CompanyResponse;
import dev.duynguyen.jobtracker.company.dto.CreateCompanyRequest;
import dev.duynguyen.jobtracker.company.dto.UpdateCompanyRequest;

/**
 * Company CRUD, plus the two rules that make the company↔application split safe.
 *
 * <p><strong>Rename cascades.</strong> Every application carries a denormalized
 * {@code companyName} so list views and search need no lookup. That copy has to be
 * re-written here, or the data drifts and search silently stops finding renamed companies
 * (SCHEMA.md §1).
 *
 * <p><strong>Delete blocks rather than cascades.</strong> Decided in CLAUDE.md §6:
 * cascading would silently destroy application history — the most valuable data in the
 * system — to satisfy a click. The 409 names the applications so it is actionable.
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companies;
    private final ApplicationRepository applications;
    private final CompanyMapper mapper;

    CompanyService(CompanyRepository companies, ApplicationRepository applications, CompanyMapper mapper) {
        this.companies = companies;
        this.applications = applications;
        this.mapper = mapper;
    }

    public List<CompanyResponse> list() {
        return companies.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public CompanyResponse get(String id) {
        return mapper.toResponse(require(id));
    }

    public CompanyResponse create(CreateCompanyRequest request) {
        requireNameAvailable(request.name(), null);

        Company company = new Company();
        mapper.apply(company, request.name(), request.website(), request.industry(),
                request.location(), request.contacts(), request.notes(), request.tags());
        return mapper.toResponse(companies.save(company));
    }

    public CompanyResponse update(String id, UpdateCompanyRequest request) {
        Company company = require(id);
        String previousName = company.getName();

        requireNameAvailable(request.name(), id);

        mapper.apply(company, request.name(), request.website(), request.industry(),
                request.location(), request.contacts(), request.notes(), request.tags());
        Company saved = companies.save(company);

        if (!saved.getName().equals(previousName)) {
            cascadeRename(saved);
        }
        return mapper.toResponse(saved);
    }

    public void delete(String id) {
        Company company = require(id);
        long referencing = applications.countByCompanyId(id);
        if (referencing > 0) {
            throw new ConflictException(
                    "Cannot delete '%s': %d application(s) still reference it. Delete or reassign them first."
                            .formatted(company.getName(), referencing));
        }
        companies.delete(company);
    }

    /** Re-writes the denormalized copy on every application belonging to this company. */
    private void cascadeRename(Company company) {
        List<Application> affected = applications.findByCompanyId(company.getId());
        affected.forEach(a -> a.setCompanyName(company.getName()));
        applications.saveAll(affected);
        log.info("company {} renamed to '{}' — updated companyName on {} application(s)",
                company.getId(), company.getName(), affected.size());
    }

    /**
     * Friendlier than letting the unique index throw. Note this check races: two concurrent
     * creates can both pass it. That is fine — the index is the real guarantee, and losing
     * the race produces a duplicate-key error rather than duplicate data.
     *
     * @param excludeId the company being updated, so it does not conflict with itself
     */
    private void requireNameAvailable(String name, String excludeId) {
        Optional<Company> existing = companies.findByNameIgnoreCase(name == null ? null : name.trim());
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new ConflictException("A company named '" + existing.get().getName() + "' already exists");
        }
    }

    Company require(String id) {
        return companies.findById(id).orElseThrow(() -> NotFoundException.of("Company", id));
    }
}
