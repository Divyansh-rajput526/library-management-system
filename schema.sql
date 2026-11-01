-- Library Management System Schema

DROP TABLE IF EXISTS issues;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS books;

-- Books catalogue
CREATE TABLE books (
    id               SERIAL PRIMARY KEY,
    title            VARCHAR(200)  NOT NULL,
    author           VARCHAR(150)  NOT NULL,
    isbn             VARCHAR(20)   UNIQUE,
    total_copies     INT           NOT NULL CHECK (total_copies >= 1),
    available_copies INT           NOT NULL CHECK (available_copies >= 0),

    CONSTRAINT copies_not_exceed_total
        CHECK (available_copies <= total_copies)
);

-- Registered library members
CREATE TABLE members (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    phone      VARCHAR(20),
    joined_on  DATE         NOT NULL DEFAULT CURRENT_DATE
);

-- Borrow transactions
CREATE TABLE issues (
    id          SERIAL PRIMARY KEY,
    book_id     INT  NOT NULL REFERENCES books(id)   ON DELETE RESTRICT,
    member_id   INT  NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    issued_on   DATE NOT NULL DEFAULT CURRENT_DATE,
    due_on      DATE NOT NULL,
    returned_on DATE,

    CONSTRAINT due_after_issue CHECK (due_on > issued_on),
    CONSTRAINT return_after_issue
        CHECK (returned_on IS NULL OR returned_on >= issued_on)
);

-- Index for member lookup
CREATE INDEX idx_issues_member ON issues(member_id);
