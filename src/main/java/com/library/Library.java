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
 * Manages database connections, transactions, and library operations.
 */
public class Library implements AutoCloseable {

    private final Connection connection;

    public Library() throws SQLException {
        String host = getEnv("DB_HOST", "localhost");
        String port = getEnv("DB_PORT", "5432");
        String name = getEnvRequired("DB_NAME");
        String user = getEnvRequired("DB_USER");
        String pass = getEnvRequired("DB_PASS");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, name);
        this.connection = DriverManager.getConnection(url, user, pass);
    }

    /**
     * Prints database metadata to verify the connection.
     */
    public void testConnection() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        System.out.println("Database product : " + meta.getDatabaseProductName());
        System.out.println("Database version : " + meta.getDatabaseProductVersion());
        System.out.println("JDBC driver      : " + meta.getDriverName() + " " + meta.getDriverVersion());
    }

    /**
     * Inserts a new book and returns the generated primary key.
     */
    public int addBook(String title, String author, String isbn, int totalCopies) throws SQLException {
        String sql = """
                INSERT INTO books (title, author, isbn, total_copies, available_copies)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, (isbn == null || isbn.isBlank()) ? null : isbn);
            ps.setInt(4, totalCopies);
            ps.setInt(5, totalCopies);

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Insert succeeded but no generated key was returned.");
    }

    /**
     * Returns all books in the catalogue ordered by title.
     */
    public List<Book> listAllBooks() throws SQLException {
        String sql = "SELECT id, title, author, isbn, total_copies, available_copies FROM books ORDER BY title";
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
     * Finds a book by primary key, or returns null if not found.
     */
    public Book findBookById(int id) throws SQLException {
        String sql = "SELECT id, title, author, isbn, total_copies, available_copies FROM books WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Searches books by title using a case-insensitive keyword match.
     */
    public List<Book> searchByTitle(String keyword) throws SQLException {
        String sql = "SELECT id, title, author, isbn, total_copies, available_copies FROM books WHERE title ILIKE ? ORDER BY title";
        List<Book> books = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
    }

    /**
     * Deletes a book by ID. Throws an exception if loan history exists.
     */
    public boolean deleteBook(int id) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new SQLException(
                    "Cannot delete book #" + id + " because it has borrowing history in the issues table. Remove those records first.", e);
            }
            throw e;
        }
    }

    /**
     * Registers a new member and returns the generated primary key.
     */
    public int addMember(String name, String email, String phone) throws SQLException {
        String sql = "INSERT INTO members (name, email, phone) VALUES (?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, (phone == null || phone.isBlank()) ? null : phone);

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new SQLException("A member with email \"" + email + "\" is already registered.", e);
            }
            throw e;
        }
        throw new SQLException("Insert succeeded but no generated key was returned.");
    }

    /**
     * Returns all registered members ordered by name.
     */
    public List<Member> listAllMembers() throws SQLException {
        String sql = "SELECT id, name, email, phone, joined_on FROM members ORDER BY name";
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
     * Finds a member by primary key, or returns null if not found.
     */
    public Member findMemberById(int id) throws SQLException {
        String sql = "SELECT id, name, email, phone, joined_on FROM members WHERE id = ?";

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
     * Finds a member by email address (case-insensitive).
     */
    public Member findMemberByEmail(String email) throws SQLException {
        String sql = "SELECT id, name, email, phone, joined_on FROM members WHERE LOWER(email) = LOWER(?)";

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

    /**
     * Issues a book to a member within a transaction using row-level locking.
     */
    public int issueBook(int bookId, int memberId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            String checkMemberSql = "SELECT id FROM members WHERE id = ?";
            try (PreparedStatement psMember = connection.prepareStatement(checkMemberSql)) {
                psMember.setInt(1, memberId);
                try (ResultSet rs = psMember.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Member with ID " + memberId + " does not exist.");
                    }
                }
            }

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

            if (availableCopies <= 0) {
                throw new SQLException("Book \"" + title + "\" (ID " + bookId + ") has no copies available to issue.");
            }

            String decrementSql = "UPDATE books SET available_copies = available_copies - 1 WHERE id = ?";
            try (PreparedStatement psUpdate = connection.prepareStatement(decrementSql)) {
                psUpdate.setInt(1, bookId);
                psUpdate.executeUpdate();
            }

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
     * Processes a book return within a transaction, restoring available copy counts.
     */
    public void returnBook(int issueId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

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

            if (returnedOn != null) {
                throw new SQLException("Book for Issue #" + issueId + " was already returned on " + returnedOn.toLocalDate() + ".");
            }

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

            String updateIssueSql = "UPDATE issues SET returned_on = CURRENT_DATE WHERE id = ?";
            try (PreparedStatement psUpdateIssue = connection.prepareStatement(updateIssueSql)) {
                psUpdateIssue.setInt(1, issueId);
                psUpdateIssue.executeUpdate();
            }

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
     * Finds an issue record by primary key.
     */
    public Issue findIssueById(int id) throws SQLException {
        String sql = "SELECT id, book_id, member_id, issued_on, due_on, returned_on FROM issues WHERE id = ?";

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

    /**
     * Retrieves all currently unreturned loans.
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
     * Retrieves the borrowing history for a specific member.
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
     * Retrieves books that have at least one copy currently in stock.
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
     * Ranks books by total number of times borrowed.
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
     * Calculates high-level library statistics.
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

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    private static Book mapRow(ResultSet rs) throws SQLException {
        return new Book(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("author"),
            rs.getString("isbn"),
            rs.getInt("total_copies"),
            rs.getInt("available_copies")
        );
    }

    private static Member mapMemberRow(ResultSet rs) throws SQLException {
        Date sqlDate = rs.getDate("joined_on");
        return new Member(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),
            sqlDate != null ? sqlDate.toLocalDate() : null
        );
    }

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

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static String getEnvRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable '" + key + "' is required but not set.");
        }
        return value;
    }
}
