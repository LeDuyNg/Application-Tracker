package dev.duynguyen.jobtracker.company;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.duynguyen.jobtracker.company.dto.CompanyResponse;
import dev.duynguyen.jobtracker.company.dto.CreateCompanyRequest;
import dev.duynguyen.jobtracker.company.dto.UpdateCompanyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Companies (CLAUDE.md §11: plural nouns, DTOs in and out, never entities).
 *
 * <p>Thin by design — every rule lives in {@link CompanyService}. The controller's only jobs
 * are HTTP concerns: status codes, the {@code Location} header, and binding. Errors are not
 * caught here; the services throw and {@code GlobalExceptionHandler} maps them.
 */
@RestController
@RequestMapping("/api/companies")
@Tag(name = "Companies", description = "Employers, and the recruiters attached to them")
public class CompanyController {

    private final CompanyService companies;

    CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping
    @Operation(summary = "List all companies, alphabetically")
    public List<CompanyResponse> list() {
        return companies.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one company")
    public CompanyResponse get(@PathVariable String id) {
        return companies.get(id);
    }

    /** 201 with a {@code Location} header — the REST-correct answer to a create. */
    @PostMapping
    @Operation(summary = "Create a company. 409 if the name is taken.")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse created = companies.create(request);
        return ResponseEntity.created(URI.create("/api/companies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a company. Renaming cascades to every application's denormalized companyName.")
    public CompanyResponse update(@PathVariable String id, @Valid @RequestBody UpdateCompanyRequest request) {
        return companies.update(id, request);
    }

    /**
     * 204, or <strong>409 when applications still reference the company</strong> — the
     * service refuses rather than cascading, because cascading would silently destroy
     * interview history to satisfy one click (CLAUDE.md §6). The error names the count.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a company. 409 if any application still references it.")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        companies.delete(id);
        return ResponseEntity.noContent().build();
    }
}
