package com.library;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Placeholder test suite.
 *
 * Real integration tests (connecting to the DB) will be added in later phases.
 * This test keeps the Maven build green without requiring a running database.
 */
public class AppTest {

    @Test
    public void bookToStringShouldContainTitle() {
        Book book = new Book("Clean Code", "Robert C. Martin", "9780132350884", 3);
        assertTrue(book.toString().contains("Clean Code"),
                   "toString() should include the book title");
    }

    @Test
    public void newBookAvailableCopiesEqualsTotal() {
        Book book = new Book("Effective Java", "Joshua Bloch", "9780134685991", 5);
        assertEquals(5, book.getAvailableCopies(),
                     "A brand-new book should have all copies available");
    }
}
