package com.library;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Book model.
 *
 * These tests run without a database — they only test Java logic in Book.java.
 * Integration tests (tests that actually talk to PostgreSQL) would require a
 * running database and are not included here; test those manually with
 * `mvn exec:java` after setting up the DB.
 */
public class AppTest {

    // -------------------------------------------------------------------------
    // Constructor and basic getters
    // -------------------------------------------------------------------------

    @Test
    public void newBookShouldStoreTitle() {
        Book book = new Book("Clean Code", "Robert C. Martin", "9780132350884", 3);
        assertEquals("Clean Code", book.getTitle());
    }

    @Test
    public void newBookShouldStoreAuthor() {
        Book book = new Book("Clean Code", "Robert C. Martin", "9780132350884", 3);
        assertEquals("Robert C. Martin", book.getAuthor());
    }

    @Test
    public void newBookAvailableCopiesEqualsTotal() {
        // When a book is added fresh, all copies should be available.
        Book book = new Book("Effective Java", "Joshua Bloch", "9780134685991", 5);
        assertEquals(5, book.getAvailableCopies(),
                "A brand-new book should have all copies available");
    }

    @Test
    public void dbConstructorShouldSetAllFields() {
        // This constructor is used when reading a row back from the database.
        Book book = new Book(42, "The Pragmatic Programmer", "Hunt & Thomas",
                "9780135957059", 4, 2);
        assertEquals(42, book.getId());
        assertEquals(4, book.getTotalCopies());
        assertEquals(2, book.getAvailableCopies());
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    public void toStringShouldContainTitle() {
        Book book = new Book("Clean Code", "Robert C. Martin", "9780132350884", 3);
        assertTrue(book.toString().contains("Clean Code"),
                "toString() should include the book title");
    }

    @Test
    public void toStringShouldContainAvailability() {
        Book book = new Book(1, "Design Patterns", "GoF", "9780201633610", 3, 1);
        // Format is "Available: 1/3"
        assertTrue(book.toString().contains("1/3"),
                "toString() should show availableCopies/totalCopies");
    }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    @Test
    public void setterShouldUpdateTitle() {
        Book book = new Book("Old Title", "Author", null, 2);
        book.setTitle("New Title");
        assertEquals("New Title", book.getTitle());
    }

    @Test
    public void setterShouldUpdateAvailableCopies() {
        Book book = new Book(1, "Title", "Author", null, 5, 5);
        book.setAvailableCopies(3);
        assertEquals(3, book.getAvailableCopies());
    }

    // -------------------------------------------------------------------------
    // Null ISBN
    // -------------------------------------------------------------------------

    @Test
    public void bookWithNullIsbnShouldNotThrow() {
        // ISBN is optional in the schema; Book must accept null gracefully.
        Book book = new Book("No ISBN Book", "Some Author", null, 1);
        assertNull(book.getIsbn());
        // toString() must also work when isbn is null
        assertNotNull(book.toString());
    }

    // =========================================================================
    // Member model tests  (Phase 3)
    // All tests run without a database — only Member.java logic is tested.
    // =========================================================================

    @Test
    public void newMemberShouldStoreName() {
        Member m = new Member("Alice", "alice@example.com", "9876543210");
        assertEquals("Alice", m.getName());
    }

    @Test
    public void newMemberShouldStoreEmail() {
        Member m = new Member("Alice", "alice@example.com", "9876543210");
        assertEquals("alice@example.com", m.getEmail());
    }

    @Test
    public void newMemberIdShouldBeZeroBeforeInsert() {
        // The 3-arg constructor is used before INSERT; id is 0 until the DB assigns it.
        Member m = new Member("Bob", "bob@example.com", null);
        assertEquals(0, m.getId());
    }

    @Test
    public void fullMemberConstructorShouldSetAllFields() {
        // The 5-arg constructor is used when reading a row back from the database.
        LocalDate date = LocalDate.of(2024, 6, 15);
        Member m = new Member(7, "Carol", "carol@example.com", "1234567890", date);
        assertEquals(7, m.getId());
        assertEquals("Carol", m.getName());
        assertEquals("carol@example.com", m.getEmail());
        assertEquals("1234567890", m.getPhone());
        assertEquals(date, m.getJoinedOn());
    }

    @Test
    public void memberWithNullPhoneShouldNotThrow() {
        // Phone is optional in the schema; Member must accept null gracefully.
        Member m = new Member("Dave", "dave@example.com", null);
        assertNull(m.getPhone());
        assertNotNull(m.toString());   // toString() must handle null phone
    }

    @Test
    public void memberToStringShouldContainName() {
        Member m = new Member(3, "Eve", "eve@example.com", "555-1234",
                LocalDate.of(2025, 1, 10));
        assertTrue(m.toString().contains("Eve"),
                "toString() should include the member's name");
    }

    @Test
    public void memberToStringShouldContainEmail() {
        Member m = new Member(4, "Frank", "frank@uni.edu", null,
                LocalDate.of(2025, 3, 20));
        assertTrue(m.toString().contains("frank@uni.edu"),
                "toString() should include the member's email");
    }

    @Test
    public void memberToStringShouldShowNAForMissingPhone() {
        Member m = new Member(5, "Grace", "grace@example.com", null, null);
        assertTrue(m.toString().contains("N/A"),
                "toString() should show N/A when phone is null");
    }

    // =========================================================================
    // Issue model tests (Phase 4)
    // All tests run without a database — only Issue.java logic is tested.
    // =========================================================================

    @Test
    public void newIssueShouldStoreBookAndMemberId() {
        LocalDate due = LocalDate.now().plusDays(14);
        Issue issue = new Issue(10, 20, due);
        assertEquals(10, issue.getBookId());
        assertEquals(20, issue.getMemberId());
        assertEquals(due, issue.getDueOn());
        assertEquals(0, issue.getId());
        assertNull(issue.getIssuedOn());
        assertNull(issue.getReturnedOn());
    }

    @Test
    public void fullIssueConstructorShouldSetAllFields() {
        LocalDate issued = LocalDate.of(2026, 11, 1);
        LocalDate due = LocalDate.of(2026, 11, 15);
        LocalDate returned = LocalDate.of(2026, 11, 10);

        Issue issue = new Issue(1, 101, 202, issued, due, returned);
        assertEquals(1, issue.getId());
        assertEquals(101, issue.getBookId());
        assertEquals(202, issue.getMemberId());
        assertEquals(issued, issue.getIssuedOn());
        assertEquals(due, issue.getDueOn());
        assertEquals(returned, issue.getReturnedOn());
    }

    @Test
    public void activeIssueShouldReportNotReturned() {
        LocalDate due = LocalDate.now().plusDays(14);
        Issue issue = new Issue(5, 1, 1, LocalDate.now(), due, null);
        assertFalse(issue.isReturned(), "Issue without return date should be active/unreturned");
        assertTrue(issue.toString().contains("Active loan"),
                "toString() should describe unreturned loan as Active");
    }

    @Test
    public void returnedIssueShouldReportReturned() {
        LocalDate issued = LocalDate.now().minusDays(5);
        LocalDate due = LocalDate.now().plusDays(9);
        LocalDate returned = LocalDate.now();

        Issue issue = new Issue(6, 1, 1, issued, due, returned);
        assertTrue(issue.isReturned(), "Issue with return date should be returned");
        assertTrue(issue.toString().contains("Returned on"),
                "toString() should indicate return date");
    }

    // =========================================================================
    // Report record unit tests (Phase 5)
    // =========================================================================

    @Test
    public void activeLoanRecordShouldHoldFields() {
        LocalDate issued = LocalDate.of(2026, 11, 1);
        LocalDate due = LocalDate.of(2026, 11, 15);
        Library.ActiveLoan loan = new Library.ActiveLoan(12, "Operating Systems", "Alice", issued, due);
        assertEquals(12, loan.issueId());
        assertEquals("Operating Systems", loan.bookTitle());
        assertEquals("Alice", loan.memberName());
        assertEquals(issued, loan.issuedOn());
        assertEquals(due, loan.dueOn());
    }

    @Test
    public void borrowHistoryRecordShouldCheckReturnStatus() {
        Library.BorrowHistory active = new Library.BorrowHistory(1, "Clean Code", LocalDate.now(), LocalDate.now().plusDays(14), null);
        assertFalse(active.isReturned());

        Library.BorrowHistory returned = new Library.BorrowHistory(2, "Clean Code", LocalDate.now().minusDays(10), LocalDate.now().plusDays(4), LocalDate.now());
        assertTrue(returned.isReturned());
    }

    @Test
    public void bookStatRecordShouldHoldBorrowCount() {
        Library.BookStat stat = new Library.BookStat(5, "Database Systems", "Silberschatz", 42);
        assertEquals(5, stat.bookId());
        assertEquals("Database Systems", stat.title());
        assertEquals("Silberschatz", stat.author());
        assertEquals(42, stat.borrowCount());
    }

    @Test
    public void summaryStatsRecordShouldHoldMetrics() {
        Library.SummaryStats stats = new Library.SummaryStats(10, 50, 45, 20, 5);
        assertEquals(10, stats.totalBooks());
        assertEquals(50, stats.totalCopies());
        assertEquals(45, stats.availableCopies());
        assertEquals(20, stats.totalMembers());
        assertEquals(5, stats.activeIssues());
    }
}
