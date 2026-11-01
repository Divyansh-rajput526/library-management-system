package com.library;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Library manages the JDBC connection and all database operations.
 *
 * Credentials are read from environment variables so no passwords appear
 * in source code:
 *   DB_HOST   (default: localhost)
 *   DB_PORT   (default: 5432)
 *   DB_NAME   (required)
 *   DB_USER   (required)
 *   DB_PASS   (required)
 *
 * Implements AutoCloseable so it works cleanly inside try-with-resources.
 *
 * --- Key JDBC concepts used here ---
 * Connection      : the live link between Java and PostgreSQL.
 * PreparedStatement: a precompiled SQL template with '?' placeholders.
 *                   The driver fills in the values safely, preventing
 *                   SQL injection attacks.
 * ResultSet       : a cursor over the rows returned by a SELECT query.
 * Statement.RETURN_GENERATED_KEYS : asks the DB to send back the new
 *                   auto-generated primary key after an INSERT.
 */
public class Library implements AutoCloseable {

    private final Connection connection;

    // -------------------------------------------------------------------------
    // Constructor – opens the connection once; reused for all operations.
    // -------------------------------------------------------------------------
    public Library() throws SQLException {
        String host = getEnv("DB_HOST", "localhost");
        String port = getEnv("DB_PORT", "5432");
        String name = getEnvRequired("DB_NAME");
        String user = getEnvRequired("DB_USER");
        String pass = getEnvRequired("DB_PASS");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, name);

        System.out.println("Connecting to: " + url);
        this.connection = DriverManager.getConnection(url, user, pass);
        System.out.println("Connection established successfully.\n");
    }

    // -------------------------------------------------------------------------
    // Phase 1 – connection test (kept for reference / startup check)
    // -------------------------------------------------------------------------

    /**
     * Prints basic metadata about the connected PostgreSQL server.
     */
    public void testConnection() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        System.out.println("Database product : " + meta.getDatabaseProductName());
        System.out.println("Database version : " + meta.getDatabaseProductVersion());
        System.out.println("JDBC driver      : " + meta.getDriverName()
                           + " " + meta.getDriverVersion());
    }

    // =========================================================================
    // Phase 2 – Book Management
    // =========================================================================

    /**
     * Inserts a new book into the `books` table and returns the generated id.
     *
     * Why PreparedStatement?
     *   If we built the SQL by pasting user input into a string like:
     *     "INSERT INTO books ... VALUES ('" + title + "', ...)"
     *   a malicious user could type SQL commands as the title and corrupt the
     *   database (SQL injection). PreparedStatement uses '?' placeholders and
     *   lets the JDBC driver safely escape all user-supplied values.
     *
     * RETURN_GENERATED_KEYS tells PostgreSQL to send back the new `id` value
     * that SERIAL assigned automatically.
     */
    public int addBook(String title, String author, String isbn, int totalCopies)
            throws SQLException {

        String sql = """
                INSERT INTO books (title, author, isbn, total_copies, available_copies)
                VALUES (?, ?, ?, ?, ?)
                """;

        // RETURN_GENERATED_KEYS → we get the new primary key back
        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, title);
            ps.setString(2, author);
            // isbn may be blank or null; store NULL in that case
            ps.setString(3, (isbn == null || isbn.isBlank()) ? null : isbn);
            ps.setInt(4, totalCopies);
            ps.setInt(5, totalCopies);   // new book: all copies available

            ps.executeUpdate();

            // Read the auto-generated id from the ResultSet PostgreSQL returns
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Insert succeeded but no generated key was returned.");
    }

    /**
     * Returns every book in the catalogue, ordered by title.
     *
     * ResultSet works like a cursor: it starts before the first row and
     * rs.next() advances it one row at a time, returning false when done.
     * We read each column by name, build a Book object, and collect them.
     */
    public List<Book> listAllBooks() throws SQLException {
        String sql = "SELECT id, title, author, isbn, total_copies, available_copies "
                   + "FROM books ORDER BY title";

        List<Book> books = new ArrayList<>();

        // try-with-resources closes Statement and ResultSet automatically
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                books.add(mapRow(rs));
            }
        }
        return books;
    }

    /**
     * Finds a single book by its primary key.
     * Returns null if no book with that id exists.
     *
     * 'SELECT ... WHERE id = ?' is the simplest PreparedStatement example:
     * one placeholder, one setInt call.
     */
    public Book findBookById(int id) throws SQLException {
        String sql = "SELECT id, title, author, isbn, total_copies, available_copies "
                   + "FROM books WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;   // not found
    }

    /**
     * Searches for books whose title contains the given keyword (case-insensitive).
     *
     * ILIKE is PostgreSQL's case-insensitive version of LIKE.
     * The '%' wildcards mean "anything before or after the keyword".
     * We wrap the keyword in '%' in Java, not in SQL, so the PreparedStatement
     * placeholder still receives a single safe string.
     */
    public List<Book> searchByTitle(String keyword) throws SQLException {
        String sql = "SELECT id, title, author, isbn, total_copies, available_copies "
                   + "FROM books WHERE title ILIKE ? ORDER BY title";

        List<Book> books = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");   // e.g. '%java%'

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
    }

    /**
     * Deletes a book by id.
     *
     * The `issues` table has a FOREIGN KEY on book_id with ON DELETE RESTRICT,
     * which means PostgreSQL will REFUSE the delete if any issue row references
     * this book — even old, returned ones.  We catch that specific error
     * (SQLState "23503" = foreign_key_violation) and show a friendly message
     * instead of a raw stack trace.
     *
     * Returns true if a row was deleted, false if the id didn't exist.
     */
    public boolean deleteBook(int id) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            // SQLState 23503 = foreign_key_violation (PostgreSQL)
            if ("23503".equals(e.getSQLState())) {
                throw new SQLException(
                    "Cannot delete book #" + id + " because it has borrowing history "
                    + "in the issues table. Remove those records first.", e);
            }
            throw e;   // re-throw anything else unchanged
        }
    }

    // =========================================================================
    // Phase 3 – Member Management
    // =========================================================================

    /**
     * Inserts a new member into the `members` table and returns the generated id.
     *
     * `joined_on` is NOT supplied here — the database column has DEFAULT CURRENT_DATE,
     * so PostgreSQL fills it in automatically.  This is a good DBMS concept to show:
     * defaults belong in the schema, not scattered across application code.
     *
     * Email uniqueness is enforced by a UNIQUE constraint in the schema.
     * We catch SQLState "23505" (unique_violation) and throw a friendly message.
     */
    public int addMember(String name, String email, String phone) throws SQLException {
        String sql = "INSERT INTO members (name, email, phone) VALUES (?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, email);
            // phone is optional — store NULL when blank or null
            ps.setString(3, (phone == null || phone.isBlank()) ? null : phone);

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new SQLException(
                    "A member with email \"" + email + "\" is already registered.", e);
            }
            throw e;
        }
        throw new SQLException("Insert succeeded but no generated key was returned.");
    }

    /**
     * Returns every member, ordered alphabetically by name.
     *
     * rs.getDate("joined_on") returns a java.sql.Date.
     * We call .toLocalDate() to convert it to the cleaner java.time.LocalDate
     * that Member.java uses.
     */
    public List<Member> listAllMembers() throws SQLException {
        String sql = "SELECT id, name, email, phone, joined_on "
                   + "FROM members ORDER BY name";

        List<Member> members = new ArrayList<>();

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                members.add(mapMemberRow(rs));
            }
        }
        return members;
    }

    /**
     * Finds a single member by primary key.
     * Returns null if no member with that id exists.
     */
    public Member findMemberById(int id) throws SQLException {
        String sql = "SELECT id, name, email, phone, joined_on "
                   + "FROM members WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapMemberRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds a member by their email address (exact match, case-insensitive).
     *
     * LOWER() on both sides makes the comparison case-insensitive without
     * needing a special column or index — good enough for a small demo.
     * Returns null if not found.
     */
    public Member findMemberByEmail(String email) throws SQLException {
        String sql = "SELECT id, name, email, phone, joined_on "
                   + "FROM members WHERE LOWER(email) = LOWER(?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapMemberRow(rs);
                }
            }
        }
        return null;
    }

    // =========================================================================
    // Phase 4 – Book Circulation (Issue & Return)
    // =========================================================================

    /**
     * Issues a book to a registered member using a JDBC transaction and row locking.
     *
     * Transaction & Concurrency Concepts:
     * 1. setAutoCommit(false) begins a transaction block in JDBC.
     * 2. SELECT ... FOR UPDATE locks the book row in PostgreSQL. Any concurrent
     *    transaction attempting to issue the same book will wait until this transaction
     *    completes, preventing a race condition where the last copy is issued twice.
     * 3. Verifies both book and member exist.
     * 4. Verifies available_copies > 0 before decrementing.
     * 5. Inserts into the issues table with due_on set to CURRENT_DATE + 14.
     * 6. Both operations (decrement copies + insert issue) either commit together or
     *    roll back together (Atomicity in ACID).
     */
    public int issueBook(int bookId, int memberId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            // 1. Verify member exists
            String checkMemberSql = "SELECT id FROM members WHERE id = ?";
            try (PreparedStatement psMember = connection.prepareStatement(checkMemberSql)) {
                psMember.setInt(1, memberId);
                try (ResultSet rs = psMember.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Member with ID " + memberId + " does not exist.");
                    }
                }
            }

            // 2. Lock book row to prevent concurrent claims on the last copy
            String lockBookSql = "SELECT id, title, available_copies FROM books WHERE id = ? FOR UPDATE";
            String title;
            int availableCopies;
            try (PreparedStatement psBook = connection.prepareStatement(lockBookSql)) {
                psBook.setInt(1, bookId);
                try (ResultSet rs = psBook.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Book with ID " + bookId + " does not exist.");
                    }
                    title = rs.getString("title");
                    availableCopies = rs.getInt("available_copies");
                }
            }

            // 3. Verify availability
            if (availableCopies <= 0) {
                throw new SQLException("Book \"" + title + "\" (ID " + bookId + ") has no copies available to issue.");
            }

            // 4. Decrement available copies
            String decrementSql = "UPDATE books SET available_copies = available_copies - 1 WHERE id = ?";
            try (PreparedStatement psUpdate = connection.prepareStatement(decrementSql)) {
                psUpdate.setInt(1, bookId);
                psUpdate.executeUpdate();
            }

            // 5. Insert loan record into issues table (default issued_on = CURRENT_DATE, due_on = CURRENT_DATE + 14)
            String insertIssueSql = "INSERT INTO issues (book_id, member_id, due_on) VALUES (?, ?, CURRENT_DATE + 14)";
            int issueId;
            try (PreparedStatement psIssue = connection.prepareStatement(insertIssueSql, Statement.RETURN_GENERATED_KEYS)) {
                psIssue.setInt(1, bookId);
                psIssue.setInt(2, memberId);
                psIssue.executeUpdate();

                try (ResultSet keys = psIssue.getGeneratedKeys()) {
                    if (keys.next()) {
                        issueId = keys.getInt(1);
                    } else {
                        throw new SQLException("Insert into issues succeeded but no generated ID was returned.");
                    }
                }
            }

            connection.commit();
            return issueId;

        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                System.err.println("Failed to restore auto-commit mode: " + e.getMessage());
            }
        }
    }

    /**
     * Returns an issued book using its issue ID within a JDBC transaction.
     *
     * Transaction & Integrity Concepts:
     * 1. SELECT ... FOR UPDATE locks the issue row and checks that it exists and returned_on IS NULL.
     * 2. SELECT ... FOR UPDATE locks the associated book row and checks that available_copies < total_copies,
     *    preventing any return from increasing available copies beyond total copies.
     * 3. Sets returned_on = CURRENT_DATE in issues table.
     * 4. Increments available_copies in books table.
     * 5. Commits both changes atomically; rolls back on any error.
     */
    public void returnBook(int issueId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            // 1. Lock and inspect issue row
            String lockIssueSql = "SELECT id, book_id, member_id, returned_on FROM issues WHERE id = ? FOR UPDATE";
            int bookId;
            Date returnedOn;
            try (PreparedStatement psIssue = connection.prepareStatement(lockIssueSql)) {
                psIssue.setInt(1, issueId);
                try (ResultSet rs = psIssue.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Issue record #" + issueId + " does not exist.");
                    }
                    bookId = rs.getInt("book_id");
                    returnedOn = rs.getDate("returned_on");
                }
            }

            // 2. Reject repeated return
            if (returnedOn != null) {
                throw new SQLException("Book for Issue #" + issueId + " was already returned on " + returnedOn.toLocalDate() + ".");
            }

            // 3. Lock book and ensure available copies will not exceed total copies
            String lockBookSql = "SELECT id, title, total_copies, available_copies FROM books WHERE id = ? FOR UPDATE";
            try (PreparedStatement psBook = connection.prepareStatement(lockBookSql)) {
                psBook.setInt(1, bookId);
                try (ResultSet rs = psBook.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Referenced book #" + bookId + " does not exist.");
                    }
                    int totalCopies = rs.getInt("total_copies");
                    int availableCopies = rs.getInt("available_copies");
                    if (availableCopies >= totalCopies) {
                        throw new SQLException("Cannot return: available copies (" + availableCopies
                                + ") already equal or exceed total copies (" + totalCopies + ").");
                    }
                }
            }

            // 4. Update returned_on to CURRENT_DATE
            String updateIssueSql = "UPDATE issues SET returned_on = CURRENT_DATE WHERE id = ?";
            try (PreparedStatement psUpdateIssue = connection.prepareStatement(updateIssueSql)) {
                psUpdateIssue.setInt(1, issueId);
                psUpdateIssue.executeUpdate();
            }

            // 5. Increment available copies
            String incrementSql = "UPDATE books SET available_copies = available_copies + 1 WHERE id = ?";
            try (PreparedStatement psUpdateBook = connection.prepareStatement(incrementSql)) {
                psUpdateBook.setInt(1, bookId);
                psUpdateBook.executeUpdate();
            }

            connection.commit();

        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                System.err.println("Failed to restore auto-commit mode: " + e.getMessage());
            }
        }
    }

    /**
     * Looks up an issue record by primary key.
     */
    public Issue findIssueById(int id) throws SQLException {
        String sql = "SELECT id, book_id, member_id, issued_on, due_on, returned_on "
                   + "FROM issues WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapIssueRow(rs);
                }
            }
        }
        return null;
    }

    // =========================================================================
    // Phase 5 – SQL Reports
    // =========================================================================

    /**
     * Report 1: Returns all currently issued (active/unreturned) books.
     * Demonstrates an INNER JOIN across 3 related tables (issues, books, members).
     */
    public List<ActiveLoan> getCurrentlyIssuedBooks() throws SQLException {
        String sql = """
            SELECT i.id AS issue_id, b.title AS book_title, m.name AS member_name,
                   i.issued_on, i.due_on
            FROM issues i
            JOIN books b ON i.book_id = b.id
            JOIN members m ON i.member_id = m.id
            WHERE i.returned_on IS NULL
            ORDER BY i.due_on ASC
            """;

        List<ActiveLoan> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Date issuedDate = rs.getDate("issued_on");
                Date dueDate = rs.getDate("due_on");
                list.add(new ActiveLoan(
                    rs.getInt("issue_id"),
                    rs.getString("book_title"),
                    rs.getString("member_name"),
                    issuedDate != null ? issuedDate.toLocalDate() : null,
                    dueDate != null ? dueDate.toLocalDate() : null
                ));
            }
        }
        return list;
    }

    /**
     * Report 2: Returns the complete borrowing history for a specific member.
     * Demonstrates a parameterized JOIN query with PreparedStatement.
     */
    public List<BorrowHistory> getMemberBorrowingHistory(int memberId) throws SQLException {
        String sql = """
            SELECT i.id AS issue_id, b.title AS book_title,
                   i.issued_on, i.due_on, i.returned_on
            FROM issues i
            JOIN books b ON i.book_id = b.id
            WHERE i.member_id = ?
            ORDER BY i.issued_on DESC, i.id DESC
            """;

        List<BorrowHistory> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date issuedDate = rs.getDate("issued_on");
                    Date dueDate = rs.getDate("due_on");
                    Date returnedDate = rs.getDate("returned_on");
                    list.add(new BorrowHistory(
                        rs.getInt("issue_id"),
                        rs.getString("book_title"),
                        issuedDate != null ? issuedDate.toLocalDate() : null,
                        dueDate != null ? dueDate.toLocalDate() : null,
                        returnedDate != null ? returnedDate.toLocalDate() : null
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Report 3: Returns books that currently have at least one copy in stock.
     * Reuses the Book model and demonstrates a filtered SELECT query.
     */
    public List<Book> getAvailableBooks() throws SQLException {
        String sql = """
            SELECT id, title, author, isbn, total_copies, available_copies
            FROM books
            WHERE available_copies > 0
            ORDER BY title ASC
            """;

        List<Book> books = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                books.add(mapRow(rs));
            }
        }
        return books;
    }

    /**
     * Report 4: Ranks books by total times borrowed.
     * Demonstrates LEFT JOIN, GROUP BY, aggregate function COUNT(), and ORDER BY.
     * Using LEFT JOIN ensures books with 0 borrows are still included.
     */
    public List<BookStat> getMostBorrowedBooks() throws SQLException {
        String sql = """
            SELECT b.id, b.title, b.author, COUNT(i.id) AS borrow_count
            FROM books b
            LEFT JOIN issues i ON b.id = i.book_id
            GROUP BY b.id, b.title, b.author
            ORDER BY borrow_count DESC, b.title ASC
            """;

        List<BookStat> stats = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                stats.add(new BookStat(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("author"),
                    rs.getInt("borrow_count")
                ));
            }
        }
        return stats;
    }

    /**
     * Report 5: Computes high-level library summary metrics in a single query.
     * Demonstrates scalar subqueries and aggregate functions (COUNT, SUM, COALESCE).
     */
    public SummaryStats getSummaryStatistics() throws SQLException {
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM books) AS total_books,
                (SELECT COALESCE(SUM(total_copies), 0) FROM books) AS total_copies,
                (SELECT COALESCE(SUM(available_copies), 0) FROM books) AS available_copies,
                (SELECT COUNT(*) FROM members) AS total_members,
                (SELECT COUNT(*) FROM issues WHERE returned_on IS NULL) AS active_issues
            """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return new SummaryStats(
                    rs.getInt("total_books"),
                    rs.getInt("total_copies"),
                    rs.getInt("available_copies"),
                    rs.getInt("total_members"),
                    rs.getInt("active_issues")
                );
            }
        }
        return new SummaryStats(0, 0, 0, 0, 0);
    }

    // -------------------------------------------------------------------------
    // Report Data Transfer Objects (Modern Java 17 Records)
    // -------------------------------------------------------------------------

    public static record ActiveLoan(
        int issueId,
        String bookTitle,
        String memberName,
        LocalDate issuedOn,
        LocalDate dueOn
    ) {}

    public static record BorrowHistory(
        int issueId,
        String bookTitle,
        LocalDate issuedOn,
        LocalDate dueOn,
        LocalDate returnedOn
    ) {
        public boolean isReturned() {
            return returnedOn != null;
        }
    }

    public static record BookStat(
        int bookId,
        String title,
        String author,
        int borrowCount
    ) {}

    public static record SummaryStats(
        int totalBooks,
        int totalCopies,
        int availableCopies,
        int totalMembers,
        int activeIssues
    ) {}

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("\nConnection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts the current ResultSet row into a Book object.
     * Centralising this avoids repeating the column-name strings in every method.
     */
    private static Book mapRow(ResultSet rs) throws SQLException {
        return new Book(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("author"),
            rs.getString("isbn"),          // may be null; Book handles that
            rs.getInt("total_copies"),
            rs.getInt("available_copies")
        );
    }

    /**
     * Converts the current ResultSet row into a Member object.
     * rs.getDate() returns java.sql.Date; .toLocalDate() converts it to
     * java.time.LocalDate, which is the modern Java date type.
     */
    private static Member mapMemberRow(ResultSet rs) throws SQLException {
        Date sqlDate = rs.getDate("joined_on");
        return new Member(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),         // may be null
            sqlDate != null ? sqlDate.toLocalDate() : null
        );
    }

    /**
     * Converts the current ResultSet row into an Issue object.
     */
    private static Issue mapIssueRow(ResultSet rs) throws SQLException {
        Date issuedDate = rs.getDate("issued_on");
        Date dueDate = rs.getDate("due_on");
        Date returnedDate = rs.getDate("returned_on");

        return new Issue(
            rs.getInt("id"),
            rs.getInt("book_id"),
            rs.getInt("member_id"),
            issuedDate != null ? issuedDate.toLocalDate() : null,
            dueDate != null ? dueDate.toLocalDate() : null,
            returnedDate != null ? returnedDate.toLocalDate() : null
        );
    }

    /** Returns the env var value, or the supplied default when it is absent. */
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /** Returns the env var value, or throws a clear error when it is absent. */
    private static String getEnvRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Environment variable '" + key + "' is required but not set.");
        }
        return value;
    }
}
