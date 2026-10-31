package com.library;

/**
 * Book is a plain model class (POJO) that demonstrates encapsulation:
 *   - fields are private
 *   - access is through public getters / setters
 *   - a constructor lets you build a fully-initialised object in one line
 *
 * It maps directly to the `books` table in the database.
 * (Member and Issue models will be added in later phases.)
 */
public class Book {

    // OOP concept: private fields — data is hidden from the outside.
    private int    id;
    private String title;
    private String author;
    private String isbn;
    private int    totalCopies;
    private int    availableCopies;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Used when reading a book back from the database (id is known). */
    public Book(int id, String title, String author,
                String isbn, int totalCopies, int availableCopies) {
        this.id              = id;
        this.title           = title;
        this.author          = author;
        this.isbn            = isbn;
        this.totalCopies     = totalCopies;
        this.availableCopies = availableCopies;
    }

    /** Used when inserting a new book (the database generates the id). */
    public Book(String title, String author, String isbn, int totalCopies) {
        this(0, title, author, isbn, totalCopies, totalCopies);
    }

    // -------------------------------------------------------------------------
    // Getters  (read-only access to private fields)
    // -------------------------------------------------------------------------

    public int    getId()              { return id; }
    public String getTitle()           { return title; }
    public String getAuthor()          { return author; }
    public String getIsbn()            { return isbn; }
    public int    getTotalCopies()     { return totalCopies; }
    public int    getAvailableCopies() { return availableCopies; }

    // -------------------------------------------------------------------------
    // Setters  (controlled write access)
    // -------------------------------------------------------------------------

    public void setId(int id)                           { this.id = id; }
    public void setTitle(String title)                  { this.title = title; }
    public void setAuthor(String author)                { this.author = author; }
    public void setIsbn(String isbn)                    { this.isbn = isbn; }
    public void setTotalCopies(int totalCopies)         { this.totalCopies = totalCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    // -------------------------------------------------------------------------
    // toString  — convenient for printing search results
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("[%d] \"%s\" by %s  |  ISBN: %s  |  Available: %d/%d",
                id, title, author, isbn, availableCopies, totalCopies);
    }
}

