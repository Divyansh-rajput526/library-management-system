package com.library;

import java.time.LocalDate;

/**
 * Member is a plain model class (POJO) that represents one row in the
 * `members` table.  It demonstrates the same OOP concept as Book.java:
 *
 *   Encapsulation — fields are private; the outside world accesses them
 *   only through public getters.  This keeps data consistent and gives
 *   us a single place to add validation later if needed.
 *
 * Column mapping:
 *   members.id        → int    id
 *   members.name      → String name
 *   members.email     → String email
 *   members.phone     → String phone   (nullable in the DB)
 *   members.joined_on → LocalDate joinedOn
 */
public class Member {

    // OOP concept: private fields — data is hidden from the outside.
    private int       id;
    private String    name;
    private String    email;
    private String    phone;       // optional — may be null
    private LocalDate joinedOn;    // maps to SQL DATE

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Full constructor — used when reading a member back from the database
     * (all five columns are known).
     */
    public Member(int id, String name, String email, String phone, LocalDate joinedOn) {
        this.id       = id;
        this.name     = name;
        this.email    = email;
        this.phone    = phone;
        this.joinedOn = joinedOn;
    }

    /**
     * New-member constructor — used before inserting into the database.
     * The database assigns `id` via SERIAL and `joined_on` via DEFAULT CURRENT_DATE,
     * so we do not need those values here.
     */
    public Member(String name, String email, String phone) {
        this(0, name, email, phone, null);
    }

    // -------------------------------------------------------------------------
    // Getters  (read-only access to private fields)
    // -------------------------------------------------------------------------

    public int       getId()       { return id; }
    public String    getName()     { return name; }
    public String    getEmail()    { return email; }
    public String    getPhone()    { return phone; }
    public LocalDate getJoinedOn() { return joinedOn; }

    // -------------------------------------------------------------------------
    // toString  — used when printing search results to the console
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        String phoneDisplay = (phone != null && !phone.isBlank()) ? phone : "N/A";
        String dateDisplay  = (joinedOn != null) ? joinedOn.toString() : "N/A";
        return String.format("[%d] %s  |  Email: %s  |  Phone: %s  |  Joined: %s",
                id, name, email, phoneDisplay, dateDisplay);
    }
}

