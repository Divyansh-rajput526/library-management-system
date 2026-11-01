package com.library;

import java.time.LocalDate;

/**
 * Issue is a model class representing one borrowing transaction from the
 * `issues` table. It demonstrates encapsulation:
 *   - private fields
 *   - constructors for database mapping and new issues
 *   - public getters and helper methods
 *
 * Column mapping:
 *   issues.id          → int       id
 *   issues.book_id     → int       bookId
 *   issues.member_id   → int       memberId
 *   issues.issued_on   → LocalDate issuedOn
 *   issues.due_on      → LocalDate dueOn
 *   issues.returned_on → LocalDate returnedOn (null means currently on loan)
 */
public class Issue {

    private int       id;
    private int       bookId;
    private int       memberId;
    private LocalDate issuedOn;
    private LocalDate dueOn;
    private LocalDate returnedOn;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Full constructor used when reading an issue row from PostgreSQL. */
    public Issue(int id, int bookId, int memberId,
                 LocalDate issuedOn, LocalDate dueOn, LocalDate returnedOn) {
        this.id         = id;
        this.bookId     = bookId;
        this.memberId   = memberId;
        this.issuedOn   = issuedOn;
        this.dueOn      = dueOn;
        this.returnedOn = returnedOn;
    }

    /** Constructor used prior to database insertion. */
    public Issue(int bookId, int memberId, LocalDate dueOn) {
        this(0, bookId, memberId, null, dueOn, null);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int       getId()         { return id; }
    public int       getBookId()     { return bookId; }
    public int       getMemberId()   { return memberId; }
    public LocalDate getIssuedOn()   { return issuedOn; }
    public LocalDate getDueOn()      { return dueOn; }
    public LocalDate getReturnedOn() { return returnedOn; }

    /** Returns true if this issue record has already been returned. */
    public boolean isReturned() {
        return returnedOn != null;
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        String status = isReturned() ? "Returned on " + returnedOn : "Active loan (Due: " + dueOn + ")";
        String dateStr = (issuedOn != null) ? issuedOn.toString() : "N/A";
        return String.format("[Issue #%d] Book #%d -> Member #%d | Issued: %s | %s",
                id, bookId, memberId, dateStr, status);
    }
}

