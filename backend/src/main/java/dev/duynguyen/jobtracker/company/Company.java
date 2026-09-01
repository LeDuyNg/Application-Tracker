package dev.duynguyen.jobtracker.company;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An employer (SCHEMA.md §2). One company has many applications.
 *
 * <p><strong>Why this is a class and not a record.</strong> Spring Data does support
 * records, but {@code @Id} population and the {@code @CreatedDate} / {@code @LastModifiedDate}
 * auditing below both write fields <em>after</em> construction — which fights an immutable
 * record and forces copy-on-save. Mutable classes for {@code @Document} types, records for
 * DTOs (SCHEMA.md §11).
 *
 * <p>{@code name} is uniquely indexed. Pick one canonical name per employer ("Meta", not
 * "Facebook") — every application denormalizes a copy of it.
 */
@Document("companies")
public class Company {

    @Id
    private String id;

    private String name;
    private String website;
    private String industry;
    private String location;

    private List<Contact> contacts = new ArrayList<>();

    private String notes;

    /** Lowercased on save by the service. */
    private List<String> tags = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public List<Contact> getContacts() { return contacts; }
    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts == null ? new ArrayList<>() : contacts;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
