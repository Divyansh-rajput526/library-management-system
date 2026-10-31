# Library Management System

A beginner-friendly Java console application demonstrating **OOP**, **JDBC**, and **PostgreSQL** concepts.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| Build | Maven |
| Database | PostgreSQL |
| DB access | JDBC (PostgreSQL driver 42.7.4) |

## Project Structure

```
library-management-system/
├── pom.xml                                   ← dependencies & build config
├── schema.sql                                ← creates books / members / issues tables
├── src/main/java/com/library/
│   ├── Main.java                             ← entry point
│   ├── Library.java                          ← JDBC connection + DB operations
│   └── Book.java                             ← OOP model (encapsulation demo)
└── src/test/java/com/library/
    └── AppTest.java                          ← unit tests (no DB required)
```

## Setup (Manual Steps Required)

### 1 — Create the PostgreSQL database

```bash
# Connect as your PostgreSQL superuser (often 'postgres')
psql -U postgres

# Inside psql, create a dedicated user and database:
CREATE USER library_user WITH PASSWORD 'your_password';
CREATE DATABASE library_db OWNER library_user;
\q
```

### 2 — Apply the schema

```bash
psql -U library_user -d library_db -f schema.sql
```

You should see `Schema applied successfully.` at the end.

### 3 — Set environment variables

The application reads credentials from environment variables — **no passwords in code**.

**macOS / Linux (current terminal session):**

```bash
export DB_HOST=localhost   # optional, default: localhost
export DB_PORT=5432        # optional, default: 5432
export DB_NAME=library_db
export DB_USER=library_user
export DB_PASS=your_password
```

**Windows PowerShell:**

```powershell
$env:DB_NAME = "library_db"
$env:DB_USER = "library_user"
$env:DB_PASS = "your_password"
```

> **Tip:** Add these `export` lines to `~/.zshrc` (or `~/.bashrc`) so they persist across terminal sessions.

### 4 — Build and run

```bash
# Compile + run tests (no DB required):
mvn clean test

# Run the application (DB must be running and env vars set):
mvn exec:java
```

Expected output when the connection succeeds:

```
=== Library Management System ===
Connecting to: jdbc:postgresql://localhost:5432/library_db
Connection established successfully.
Database product : PostgreSQL
Database version : 16.x ...
JDBC driver      : PostgreSQL JDBC Driver 42.7.4
Connection test  : PASSED
Connection closed.
```

## Database Schema

```
books                    members
─────                    ───────
id (PK, serial)          id (PK, serial)
title                    name
author                   email (UNIQUE)
isbn (UNIQUE)            phone
total_copies             joined_on (DEFAULT today)
available_copies
                         issues
                         ──────
                         id (PK, serial)
                         book_id   → books(id)
                         member_id → members(id)
                         issued_on (DEFAULT today)
                         due_on
                         returned_on (NULL = still borrowed)
```

## Key Concepts Demonstrated

| Concept | Where |
|---|---|
| **Encapsulation** | `Book.java` — private fields, public getters/setters |
| **AutoCloseable / try-with-resources** | `Library.java` + `Main.java` |
| **JDBC connection** | `Library.java` — `DriverManager.getConnection()` |
| **Environment variables** | `Library.java` — `System.getenv()` |
| **SQL constraints** | `schema.sql` — PK, FK, UNIQUE, CHECK |
| **Referential integrity** | `schema.sql` — `ON DELETE RESTRICT` |

## Roadmap (Phases 2–3)

- [ ] Add / search / delete books (`Library.addBook`, `Library.searchBooks`)
- [ ] Register members
- [ ] Issue and return books (updates `available_copies` atomically)
- [ ] View overdue loans
- [ ] Console menu in `Main.java`

