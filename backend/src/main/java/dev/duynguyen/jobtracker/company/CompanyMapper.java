package dev.duynguyen.jobtracker.company;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.duynguyen.jobtracker.common.Tags;

import dev.duynguyen.jobtracker.company.dto.CompanyResponse;
import dev.duynguyen.jobtracker.company.dto.ContactRequest;
import dev.duynguyen.jobtracker.company.dto.ContactResponse;

/**
 * Hand-written entity ↔ DTO mapping. No MapStruct — one less tool to learn and debug, and
 * at this size generated code would hide more than it saves (CLAUDE.md §11).
 */
@Component
public class CompanyMapper {

    public CompanyResponse toResponse(Company c) {
        return new CompanyResponse(
                c.getId(),
                c.getName(),
                c.getWebsite(),
                c.getIndustry(),
                c.getLocation(),
                c.getContacts().stream().map(this::toResponse).toList(),
                c.getNotes(),
                List.copyOf(c.getTags()),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private ContactResponse toResponse(Contact c) {
        return new ContactResponse(c.getName(), c.getTitle(), c.getEmail(), c.getPhone(), c.getNotes());
    }

    /**
     * Copies request fields onto an entity — used for both create (fresh entity) and update
     * (existing one). Deliberately never touches {@code id}, {@code createdAt} or
     * {@code updatedAt}: those belong to Mongo and the auditing listeners.
     */
    public void apply(Company target, String name, String website, String industry, String location,
                      List<ContactRequest> contacts, String notes, List<String> tags) {
        target.setName(name == null ? null : name.trim());
        target.setWebsite(website);
        target.setIndustry(industry);
        target.setLocation(location);
        target.setNotes(notes);
        target.setContacts(contacts == null ? List.of() : contacts.stream().map(this::toEntity).toList());
        target.setTags(Tags.normalize(tags));
    }

    private Contact toEntity(ContactRequest r) {
        Contact c = new Contact();
        c.setName(r.name());
        c.setTitle(r.title());
        c.setEmail(r.email());
        c.setPhone(r.phone());
        c.setNotes(r.notes());
        return c;
    }

}
