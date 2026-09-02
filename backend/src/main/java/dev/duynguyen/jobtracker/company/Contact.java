package dev.duynguyen.jobtracker.company;

/**
 * A recruiter or referrer at a company (SCHEMA.md §4.1).
 *
 * <p>Embedded in {@link Company#getContacts()}. No {@code @Document} annotation and no id —
 * Spring Data maps a plain POJO straight into the parent document's array. It has no
 * independent lifecycle: you never look up a contact without the company.
 */
public class Contact {

    private String name;
    private String title;
    private String email;
    private String phone;
    private String notes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
