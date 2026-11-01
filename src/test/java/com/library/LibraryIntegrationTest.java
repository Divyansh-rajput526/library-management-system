package com.library;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests executed against a live PostgreSQL database.
 *
 * These tests are only enabled when the DB_NAME environment variable is provided.
 * When DB_NAME is not set, JUnit 5 cleanly skips them so offline builds pass.
 */
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
public class LibraryIntegrationTest {

    @Test
    public void testSuccessfulIssueAndReturnCycle() throws SQLException {
        try (Library library = new Library()) {
            // 1. Setup sample book and member
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            int bookId = library.addBook("Integration Test Book " + uniqueSuffix,
                    "Test Author", "ISBN-" + uniqueSuffix, 2);
            int memberId = library.addMember("Integration Member " + uniqueSuffix,
                    "member" + uniqueSuffix + "@test.com", "555-0199");

            // Verify initial copies
            Book initialBook = library.findBookById(bookId);
            assertNotNull(initialBook);
            assertEquals(2, initialBook.getAvailableCopies());
            assertEquals(2, initialBook.getTotalCopies());

            // 2. Issue book
            int issueId = library.issueBook(bookId, memberId);
            assertTrue(issueId > 0, "Generated issue ID should be positive");

            // Verify available copies decremented
            Book afterIssue = library.findBookById(bookId);
            assertEquals(1, afterIssue.getAvailableCopies(),
                    "Available copies must decrement by 1 after issue");

            // Verify issue record exists and is active
            Issue issue = library.findIssueById(issueId);
            assertNotNull(issue);
            assertEquals(bookId, issue.getBookId());
            assertEquals(memberId, issue.getMemberId());
            assertFalse(issue.isReturned(), "Newly issued loan must not be returned");

            // 3. Return book
            library.returnBook(issueId);

            // Verify available copies incremented back to 2
            Book afterReturn = library.findBookById(bookId);
            assertEquals(2, afterReturn.getAvailableCopies(),
                    "Available copies must increment by 1 after return");

            // Verify issue record is marked returned
            Issue returnedIssue = library.findIssueById(issueId);
            assertNotNull(returnedIssue);
            assertTrue(returnedIssue.isReturned(), "Issue record must be marked returned");
            assertNotNull(returnedIssue.getReturnedOn());
        }
    }

    @Test
    public void testIssueRejectedWhenNoCopiesAvailable() throws SQLException {
        try (Library library = new Library()) {
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            int bookId = library.addBook("Single Copy Book " + uniqueSuffix,
                    "Test Author", "ISBN-SINGLE-" + uniqueSuffix, 1);
            int member1 = library.addMember("Member One " + uniqueSuffix,
                    "m1_" + uniqueSuffix + "@test.com", null);
            int member2 = library.addMember("Member Two " + uniqueSuffix,
                    "m2_" + uniqueSuffix + "@test.com", null);

            // First issue claims the only available copy
            int issue1 = library.issueBook(bookId, member1);
            assertTrue(issue1 > 0);

            Book book = library.findBookById(bookId);
            assertEquals(0, book.getAvailableCopies());

            // Second issue for the same book must be rejected
            SQLException exception = assertThrows(SQLException.class, () -> {
                library.issueBook(bookId, member2);
            });
            assertTrue(exception.getMessage().toLowerCase().contains("no copies available")
                    || exception.getMessage().toLowerCase().contains("available"));

            // Verify available copies stayed at 0
            Book finalBook = library.findBookById(bookId);
            assertEquals(0, finalBook.getAvailableCopies());
        }
    }

    @Test
    public void testRepeatedReturnRejected() throws SQLException {
        try (Library library = new Library()) {
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            int bookId = library.addBook("Return Test Book " + uniqueSuffix,
                    "Test Author", "ISBN-RET-" + uniqueSuffix, 1);
            int memberId = library.addMember("Return Member " + uniqueSuffix,
                    "ret_" + uniqueSuffix + "@test.com", null);

            int issueId = library.issueBook(bookId, memberId);

            // First return succeeds
            library.returnBook(issueId);

            // Second return for the same issue record must be rejected
            SQLException exception = assertThrows(SQLException.class, () -> {
                library.returnBook(issueId);
            });
            assertTrue(exception.getMessage().toLowerCase().contains("already returned"));

            // Verify book available copies does not exceed total copies (stays at 1)
            Book book = library.findBookById(bookId);
            assertEquals(1, book.getAvailableCopies());
        }
    }

    @Test
    public void testRollbackWhenMemberDoesNotExist() throws SQLException {
        try (Library library = new Library()) {
            String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
            int bookId = library.addBook("Rollback Test Book " + uniqueSuffix,
                    "Test Author", "ISBN-RB-" + uniqueSuffix, 3);

            // Attempt to issue to non-existent member ID
            int nonExistentMemberId = 9999999;
            assertThrows(SQLException.class, () -> {
                library.issueBook(bookId, nonExistentMemberId);
            });

            // Verify atomicity / rollback: book copies must still be 3
            Book book = library.findBookById(bookId);
            assertEquals(3, book.getAvailableCopies(),
                    "Transaction rollback must ensure available copies remained unchanged");
        }
    }

    @Test
    public void testPhase5ReportsWorkflow() throws SQLException {
        try (Library library = new Library()) {
            String suffix = String.valueOf(System.currentTimeMillis() % 100000);

            // 1. Setup sample data
            int bookA = library.addBook("Algorithms " + suffix, "Author A", "ISBN-A-" + suffix, 2);
            int bookB = library.addBook("Networks " + suffix, "Author B", "ISBN-B-" + suffix, 1);
            int member1 = library.addMember("Student One " + suffix, "s1_" + suffix + "@uni.edu", null);
            int member2 = library.addMember("Student Two " + suffix, "s2_" + suffix + "@uni.edu", null);

            // 2. Report 3: Available books (both should be available)
            java.util.List<Book> initialAvailable = library.getAvailableBooks();
            assertTrue(initialAvailable.stream().anyMatch(b -> b.getId() == bookA));
            assertTrue(initialAvailable.stream().anyMatch(b -> b.getId() == bookB));

            // 3. Issue Book A and Book B to Member 1
            int issueA = library.issueBook(bookA, member1);
            int issueB = library.issueBook(bookB, member1);

            // 4. Report 3: Available books (Book B has 0 copies left, so should NOT be in available list)
            java.util.List<Book> availableAfterIssue = library.getAvailableBooks();
            assertTrue(availableAfterIssue.stream().anyMatch(b -> b.getId() == bookA),
                    "Book A still has 1 copy available");
            assertFalse(availableAfterIssue.stream().anyMatch(b -> b.getId() == bookB),
                    "Book B has 0 copies available and must not appear");

            // 5. Report 1: Currently issued books (both active loans present)
            java.util.List<Library.ActiveLoan> activeLoans = library.getCurrentlyIssuedBooks();
            assertTrue(activeLoans.stream().anyMatch(l -> l.issueId() == issueA));
            assertTrue(activeLoans.stream().anyMatch(l -> l.issueId() == issueB));

            // 6. Report 2: Member borrowing history
            java.util.List<Library.BorrowHistory> historyM1 = library.getMemberBorrowingHistory(member1);
            assertEquals(2, historyM1.stream().filter(h -> h.issueId() == issueA || h.issueId() == issueB).count());
            assertFalse(historyM1.get(0).isReturned(), "Newly issued loan must show as active/unreturned");

            java.util.List<Library.BorrowHistory> historyM2 = library.getMemberBorrowingHistory(member2);
            assertEquals(0, historyM2.size(), "Member with no loans should have empty history");

            // 7. Report 4: Most borrowed books
            java.util.List<Library.BookStat> stats = library.getMostBorrowedBooks();
            assertTrue(stats.stream().anyMatch(s -> s.bookId() == bookA && s.borrowCount() >= 1));
            assertTrue(stats.stream().anyMatch(s -> s.bookId() == bookB && s.borrowCount() >= 1));

            // 8. Report 5: Summary statistics
            Library.SummaryStats summary = library.getSummaryStatistics();
            assertTrue(summary.totalBooks() >= 2);
            assertTrue(summary.totalCopies() >= 3);
            assertTrue(summary.activeIssues() >= 2);

            // 9. Return Book B and verify reports update
            library.returnBook(issueB);

            // Book B is now available again
            java.util.List<Book> availableAfterReturn = library.getAvailableBooks();
            assertTrue(availableAfterReturn.stream().anyMatch(b -> b.getId() == bookB),
                    "Book B must reappear in available books after return");

            // Book B is no longer in active loans
            java.util.List<Library.ActiveLoan> activeAfterReturn = library.getCurrentlyIssuedBooks();
            assertFalse(activeAfterReturn.stream().anyMatch(l -> l.issueId() == issueB),
                    "Returned issue must not appear in active loans report");

            // Member 1 history now shows returned for Book B
            java.util.List<Library.BorrowHistory> historyM1AfterReturn = library.getMemberBorrowingHistory(member1);
            Library.BorrowHistory returnedRecord = historyM1AfterReturn.stream()
                    .filter(h -> h.issueId() == issueB)
                    .findFirst()
                    .orElseThrow();
            assertTrue(returnedRecord.isReturned(), "History must reflect returned status");
        }
    }
}

