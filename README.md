# Library Management System

A beginner-friendly Java console application demonstrating **OOP**, **JDBC**, and **PostgreSQL** concepts.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 17 (OpenJDK 17.0.20.1) |
| Build | Maven |
| Database | PostgreSQL |
| DB access | JDBC — PostgreSQL driver 42.7.4 |

## Project Structure

```
library-management-system/
├── pom.xml                                   ← dependencies & build config
├── schema.sql                                ← creates books / members / issues tables
├── src/main/java/com/library/
│   ├── Main.java     ← console menu + user input
│   ├── Library.java  ← JDBC connection + transaction-safe SQL operations
│   ├── Book.java     ← OOP model for books
│   ├── Member.java   ← OOP model for members
│   └── Issue.java    ← OOP model for book circulation records
└── src/test/java/com/library/
    ├── AppTest.java               ← unit tests (no DB required)
    └── LibraryIntegrationTest.java ← integration tests against PostgreSQL
```

---

## One-Time Setup (do this once)

### Step 1 — Create the PostgreSQL database

```bash
psql -U postgres
```
```sql
CREATE USER library_user WITH PASSWORD 'your_password';
CREATE DATABASE library_db OWNER library_user;
\q
```

### Step 2 — Apply the schema

```bash
psql -U library_user -d library_db -f schema.sql
```

You should see `Schema applied successfully.` at the end.

### Step 3 — Set environment variables

The app reads credentials from environment variables — **no passwords in source code**.

```bash
# macOS / Linux — run in the same terminal you will use for mvn exec:java
export DB_HOST=localhost    # optional, default: localhost
export DB_PORT=5432         # optional, default: 5432
export DB_NAME=library_db
export DB_USER=library_user
export DB_PASS=your_password
```

> **Tip:** Add those `export` lines to `~/.zshrc` so they persist across sessions.

---

## Running the Application

```bash
# Compile + run unit tests (no DB required):
mvn clean test

# Run the interactive console app (DB must be running + env vars must be set):
mvn exec:java
```

### What you see at startup

```
╔══════════════════════════════════╗
║   Library Management System      ║
╚══════════════════════════════════╝
Connecting to: jdbc:postgresql://localhost:5432/library_db
Connection established successfully.

Database product : PostgreSQL
Database version : 16.x ...
JDBC driver      : PostgreSQL JDBC Driver 42.7.4

──── Book Management ────────────────
  1. Add a book
  2. List all books
  3. Search books by title
  4. Find book by ID
  5. Delete a book
  0. Exit
─────────────────────────────────────
Enter choice:
```

---

## Phase 2 — Book Management Features

| Menu Option | What it does | SQL used |
|---|---|---|
| **1. Add a book** | Inserts a row into `books`; shows the new ID | `INSERT INTO books ... VALUES (?,?,?,?,?)` |
| **2. List all books** | Shows every book sorted by title | `SELECT ... FROM books ORDER BY title` |
| **3. Search by title** | Case-insensitive keyword search | `WHERE title ILIKE ?` |
| **4. Find by ID** | Looks up one book by primary key | `WHERE id = ?` |
| **5. Delete a book** | Asks for confirmation; blocked if borrow history exists | `DELETE FROM books WHERE id = ?` |

## Phase 3 — Member Management Features

| Menu Option | What it does | SQL used |
|---|---|---|
| **6. Register a member** | Inserts into `members`; shows the new ID | `INSERT INTO members (name, email, phone) VALUES (?,?,?)` |
| **7. List all members** | Shows every member sorted by name | `SELECT ... FROM members ORDER BY name` |
| **8. Find by ID** | Looks up one member by primary key | `WHERE id = ?` |
| **9. Find by email** | Case-insensitive email lookup | `WHERE LOWER(email) = LOWER(?)` |

## Phase 4 — Circulation (Issue & Return) Features

| Menu Option | What it does | SQL & Transaction Handling |
|---|---|---|
| **10. Issue a book** | Atomically decrements `available_copies` and inserts into `issues` | `SELECT ... FOR UPDATE`, `UPDATE books SET available_copies = available_copies - 1`, `INSERT INTO issues (book_id, member_id, due_on) VALUES (?, ?, CURRENT_DATE + 14)` inside transaction |
| **11. Return a book** | Marks issue returned and atomically increments `available_copies` | `SELECT ... FOR UPDATE`, `UPDATE issues SET returned_on = CURRENT_DATE`, `UPDATE books SET available_copies = available_copies + 1` inside transaction |

## Phase 5 — SQL Reports & Statistics (Option 12)

| Submenu Option | Report Description | SQL Query Structure & Concepts |
|---|---|---|
| **1. Currently issued books** | Lists all active unreturned loans with due date and overdue status | 3-table `INNER JOIN` (`issues` ⨝ `books` ⨝ `members`) with `WHERE returned_on IS NULL` |
| **2. Member borrowing history** | Displays entire borrowing ledger for a given member | Parameterized `JOIN` query (`issues` ⨝ `books`) with `WHERE member_id = ?` ordered newest first |
| **3. Available books in stock** | Lists all books that have at least 1 copy available | Filtered catalogue query with `WHERE available_copies > 0` |
| **4. Most borrowed books ranking** | Ranks all books from most borrowed to least | `LEFT JOIN` (`books` ⟕ `issues`) + `GROUP BY b.id, b.title, b.author` + `COUNT(i.id)` + `ORDER BY borrow_count DESC` |
| **5. Summary statistics** | High-level library KPIs in a clean tabular dashboard | Single query with scalar subqueries + aggregate functions (`COUNT(*)`, `SUM()`, `COALESCE()`) |

---

## Database Schema

```
books                    members
─────                    ───────
id (PK, SERIAL)          id (PK, SERIAL)
title        NOT NULL     name        NOT NULL
author       NOT NULL     email       UNIQUE NOT NULL
isbn         UNIQUE        phone
total_copies  CHECK ≥ 1   joined_on   DEFAULT today
available_copies CHECK ≥ 0

issues
──────
id (PK, SERIAL)
book_id   → books(id)    ON DELETE RESTRICT
member_id → members(id)  ON DELETE RESTRICT
issued_on  DEFAULT today
due_on     (issued_on + 14 days, set by app)
returned_on (NULL = still borrowed)
```

---

## Key Concepts for Your Presentation

| Concept | Where in the code |
|---|---|
| **Encapsulation** | `Book.java`, `Member.java`, `Issue.java` — private fields, public getters |
| **Java 17 Records** | `ActiveLoan`, `BorrowHistory`, `BookStat`, `SummaryStats` — concise immutable DTOs |
| **AutoCloseable + try-with-resources** | `Library.java` implements `AutoCloseable`; `Main.java` uses `try (Library lib = ...)` |
| **JDBC Transactions (ACID)** | `Library.issueBook()` / `returnBook()` — `setAutoCommit(false)`, `commit()`, `rollback()` |
| **Pessimistic Row Locking** | `SELECT ... FOR UPDATE` locks rows during issue/return to prevent race conditions on the last copy |
| **Concurrency Protection** | Two simultaneous issue requests cannot claim the same final copy |
| **Over-return Prevention** | Validates `available_copies < total_copies` before incrementing |
| **Duplicate Return Prevention** | Checks `returned_on IS NULL` with row lock; rejects repeated returns cleanly |
| **Relational INNER JOIN** | Report 1 (`issues` ⨝ `books` ⨝ `members`) merges 3 tables into a consolidated report |
| **Relational LEFT JOIN** | Report 4 (`books` ⟕ `issues`) preserves books with zero borrowing history |
| **Aggregation & GROUP BY** | Report 4 groups borrow events by book and uses `COUNT(i.id)` |
| **Scalar Subqueries** | Report 5 executes a single SQL query computing 5 dashboard metrics |
| **COALESCE() in SQL** | Replaces NULL sums with `0` when calculating total/available copies |
| **JDBC PreparedStatement** | Every user-input SQL in `Library.java` — `?` placeholders prevent SQL injection |
| **SQL injection prevention** | `PreparedStatement` vs. unsafe string concatenation |
| **ResultSet cursor** | `Library.listAllBooks()` / `listAllMembers()` — `while (rs.next())` pattern |
| **RETURN_GENERATED_KEYS** | `Library.addBook()`, `addMember()`, `issueBook()` — reads auto-generated PK |
| **Foreign key + ON DELETE RESTRICT** | `schema.sql` — blocks deleting a book/member with loan history |
| **SQLState error codes** | Catches `23503` (FK violation) and `23505` (unique violation) with user-friendly messages |
| **DEFAULT in SQL** | `joined_on DEFAULT CURRENT_DATE` and `issued_on DEFAULT CURRENT_DATE` |
| **java.sql.Date → LocalDate** | Converts JDBC date types to modern `java.time.LocalDate` |
| **Separation of concerns** | `Main` = I/O only · `Library` = SQL/transactions only · Models = data only |
| **Environment variables** | `System.getenv()` in `Library.java` — credentials never in source |

---

## Presentation Walkthrough (Step-by-Step Demo)

Use this quick sequence during your presentation:

1. **Architecture & Connection (30 sec)**:
   - Run `mvn exec:java`. Show the startup banner confirming PostgreSQL connection via JDBC.
   - Mention credentials loaded safely from environment variables (`System.getenv()`).
2. **OOP Encapsulation & Book Management (45 sec)**:
   - Choose **Option 1 (Add a book)**: Add "Distributed Systems", author, 2 copies.
   - Explain `Book.java` demonstrates encapsulation (private fields, public getters) and `PreparedStatement` prevents SQL injection.
3. **Member Management (30 sec)**:
   - Choose **Option 6 (Register a member)**: Add "Ada Lovelace", email `ada@example.com`.
   - Explain how `joined_on DEFAULT CURRENT_DATE` lets PostgreSQL assign registration date automatically.
4. **Circulation & ACID Transactions (60 sec)**:
   - Choose **Option 10 (Issue a book)**: Issue the book to Ada. Show generated Issue ID and `available_copies` decreased to 1.
   - Explain **ACID Atomicity**: `connection.setAutoCommit(false)`, `commit()`, and `rollback()`.
   - Explain **Pessimistic Locking**: `SELECT ... FOR UPDATE` prevents concurrent race conditions on the last available copy.
5. **SQL Reports Submenu (60 sec)**:
   - Choose **Option 12 (View reports)**:
     - **Submenu Option 1 (Active loans)**: Show 3-table `INNER JOIN` displaying Ada's open loan.
     - **Submenu Option 4 (Most borrowed)**: Show `LEFT JOIN` + `GROUP BY` + `COUNT(i.id)` ranking books.
     - **Submenu Option 5 (Summary statistics)**: Show the KPI table built from scalar subqueries and `COALESCE(SUM())`.
     - **Submenu Option 0**: Return to main menu.
6. **Return Workflow & Integrity (30 sec)**:
   - Choose **Option 11 (Return a book)**: Return the loan using its Issue ID. Show copy count restored.
   - Explain how repeated returns are rejected and copies cannot exceed total capacity.

---

## Running the Tests

### 1. Standard Unit Tests (Offline / No DB needed)
```bash
mvn clean test
```
Runs 25 unit tests across `Book`, `Member`, `Issue`, and report records. Live integration tests are cleanly skipped when `DB_NAME` is not set.

### 2. Full Suite with PostgreSQL Integration Tests
```bash
DB_NAME=library_db DB_USER=library_user DB_PASS=password mvn test
```
Runs all 30 tests (25 unit tests + 5 live PostgreSQL integration tests covering full issue/return cycles, report workflows, zero-copy rejection, repeated return rejection, and rollback on failure).

---

## Roadmap

- [x] Phase 1 — Database connection test
- [x] Phase 2 — Book management (add, list, search, find, delete)
- [x] Phase 3 — Member management (register, list, find by ID, find by email)
- [x] Phase 4 — Issue and return books (with atomic `available_copies` transactions)
- [x] Phase 5 — SQL reports & statistics (active loans, borrowing history, available books, rankings, KPIs)
