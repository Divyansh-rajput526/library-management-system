package com.library;

import java.time.LocalDate;

public class Issue {

    private int id;
    private int bookId;
    private int memberId;
    private LocalDate issuedOn;
    private LocalDate dueOn;
    private LocalDate returnedOn;

    public Issue(int id, int bookId, int memberId,
                 LocalDate issuedOn, LocalDate dueOn, LocalDate returnedOn) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.issuedOn = issuedOn;
        this.dueOn = dueOn;
        this.returnedOn = returnedOn;
    }

    public Issue(int bookId, int memberId, LocalDate dueOn) {
        this(0, bookId, memberId, null, dueOn, null);
    }

    public int getId() { return id; }
    public int getBookId() { return bookId; }
    public int getMemberId() { return memberId; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public LocalDate getDueOn() { return dueOn; }
    public LocalDate getReturnedOn() { return returnedOn; }

    public boolean isReturned() {
        return returnedOn != null;
    }

    @Override
    public String toString() {
        String status = isReturned() ? "Returned on " + returnedOn : "Active loan (Due: " + dueOn + ")";
        String dateStr = (issuedOn != null) ? issuedOn.toString() : "N/A";
        return String.format("[Issue #%d] Book #%d -> Member #%d | Issued: %s | %s",
                id, bookId, memberId, dateStr, status);
    }
}
