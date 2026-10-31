-- =============================================================================
-- Library Management System – Database Schema
-- =============================================================================
-- Apply with:  psql -U <user> -d library_db -f schema.sql
--
-- Three tables are created:
--   books   – catalogue of all books in the library
--   members – registered library members
--   issues  – records which member borrowed which book and when
-- =============================================================================

-- Drop tables in reverse dependency order (safe to re-run during development).
DROP TABLE IF EXISTS issues;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS books;

-- -----------------------------------------------------------------------------
-- 1. books
--    Stores each unique title.  available_copies is updated automatically
--    when a book is issued or returned.
-- -----------------------------------------------------------------------------
CREATE TABLE books (
    id               SERIAL PRIMARY KEY,          -- auto-incrementing PK
    title            VARCHAR(200)  NOT NULL,
    author           VARCHAR(150)  NOT NULL,
    isbn             VARCHAR(20)   UNIQUE,        -- nullable but unique when set
    total_copies     INT           NOT NULL CHECK (total_copies >= 1),
    available_copies INT           NOT NULL CHECK (available_copies >= 0),

    CONSTRAINT copies_not_exceed_total
        CHECK (available_copies <= total_copies)
);

-- -----------------------------------------------------------------------------
-- 2. members
--    Registered library members who can borrow books.
-- -----------------------------------------------------------------------------
CREATE TABLE members (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,      -- used as login identifier
    phone      VARCHAR(20),
    joined_on  DATE         NOT NULL DEFAULT CURRENT_DATE
);

-- -----------------------------------------------------------------------------
-- 3. issues
--    Records every borrow event.  A NULL returned_on means the book is still
--    out on loan.
--
--    Foreign keys enforce referential integrity:
--      - you cannot issue a non-existent book or to a non-existent member
--      - ON DELETE RESTRICT prevents deleting a book/member with open loans
-- -----------------------------------------------------------------------------
CREATE TABLE issues (
    id          SERIAL PRIMARY KEY,
    book_id     INT  NOT NULL REFERENCES books(id)   ON DELETE RESTRICT,
    member_id   INT  NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    issued_on   DATE NOT NULL DEFAULT CURRENT_DATE,
    due_on      DATE NOT NULL,                       -- set to issued_on + 14 days by app
    returned_on DATE,                                -- NULL = still borrowed

    CONSTRAINT due_after_issue CHECK (due_on > issued_on),
    CONSTRAINT return_after_issue
        CHECK (returned_on IS NULL OR returned_on >= issued_on)
);

-- Handy index: quickly find all open loans for a member.
CREATE INDEX idx_issues_member ON issues(member_id);

-- Confirm schema was applied.
SELECT 'Schema applied successfully.' AS status;

