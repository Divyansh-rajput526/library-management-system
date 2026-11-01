package com.library;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;


public class Main {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║   Library Management System      ║");
        System.out.println("╚══════════════════════════════════╝");

        try (Library library = new Library()) {
            library.testConnection();

            boolean running = true;
            while (running) {
                printMenu();
                int choice = readInt("Enter choice: ");

                switch (choice) {
                    // Books
                    case 1 -> addBook(library);
                    case 2 -> listBooks(library);
                    case 3 -> searchBooks(library);
                    case 4 -> findBookById(library);
                    case 5 -> deleteBook(library);

                    // Members
                    case 6 -> addMember(library);
                    case 7 -> listMembers(library);
                    case 8 -> findMember(library);
                    case 9 -> findMemberByEmail(library);

                    // Circulation
                    case 10 -> issueBook(library);
                    case 11 -> returnBook(library);

                    // Reports
                    case 12 -> showReportsMenu(library);

                    case 0 -> running = false;
                    default -> System.out.println("  Invalid option. Please try again.");
                }
            }
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
        }

        System.out.println("Goodbye!");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("──── Books ──────────────────────────");
        System.out.println("  1. Add a book");
        System.out.println("  2. List all books");
        System.out.println("  3. Search books by title");
        System.out.println("  4. Find book by ID");
        System.out.println("  5. Delete a book");
        System.out.println("──── Members ────────────────────────");
        System.out.println("  6. Register a member");
        System.out.println("  7. List all members");
        System.out.println("  8. Find member by ID");
        System.out.println("  9. Find member by email");
        System.out.println("──── Circulation (Loans) ────────────");
        System.out.println(" 10. Issue a book");
        System.out.println(" 11. Return a book");
        System.out.println("──── Reports & Statistics ───────────");
        System.out.println(" 12. View library reports");
        System.out.println("─────────────────────────────────────");
        System.out.println("  0. Exit");
        System.out.println("─────────────────────────────────────");
    }

    // --- Book handlers ---

    private static void addBook(Library library) {
        System.out.println("\n-- Add New Book --");

        String title  = readNonBlank("Title        : ");
        String author = readNonBlank("Author       : ");

        System.out.print("ISBN (optional, press Enter to skip): ");
        String isbn = scanner.nextLine().trim();

        int copies = readPositiveInt("Total copies : ");

        try {
            int newId = library.addBook(title, author, isbn, copies);
            System.out.println("  ✔ Book added successfully with ID = " + newId);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                System.out.println("  ✘ A book with that ISBN already exists.");
            } else {
                System.out.println("  ✘ Database error: " + e.getMessage());
            }
        }
    }

    private static void listBooks(Library library) {
        System.out.println("\n-- All Books --");
        try {
            List<Book> books = library.listAllBooks();
            if (books.isEmpty()) {
                System.out.println("  No books in the catalogue yet.");
            } else {
                books.forEach(b -> System.out.println("  " + b));
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    private static void searchBooks(Library library) {
        System.out.println("\n-- Search Books by Title --");
        String keyword = readNonBlank("Keyword: ");

        try {
            List<Book> results = library.searchByTitle(keyword);
            if (results.isEmpty()) {
                System.out.println("  No books found matching \"" + keyword + "\".");
            } else {
                System.out.println("  Found " + results.size() + " result(s):");
                results.forEach(b -> System.out.println("  " + b));
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    private static void findBookById(Library library) {
        System.out.println("\n-- Find Book by ID --");
        int id = readInt("Book ID: ");

        try {
            Book book = library.findBookById(id);
            if (book == null) {
                System.out.println("  No book found with ID = " + id + ".");
            } else {
                System.out.println("  " + book);
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    private static void deleteBook(Library library) {
        System.out.println("\n-- Delete Book --");
        int id = readInt("Book ID to delete: ");

        try {
            Book book = library.findBookById(id);
            if (book == null) {
                System.out.println("  No book found with ID = " + id + ".");
                return;
            }
            System.out.println("  You are about to delete: " + book);
            System.out.print("  Confirm? (yes/no): ");
            String confirm = scanner.nextLine().trim();

            if (!confirm.equalsIgnoreCase("yes")) {
                System.out.println("  Cancelled.");
                return;
            }

            boolean deleted = library.deleteBook(id);
            if (deleted) {
                System.out.println("  ✔ Book deleted.");
            } else {
                System.out.println("  No book was deleted (ID may have changed).");
            }

        } catch (SQLException e) {
            System.out.println("  ✘ " + e.getMessage());
        }
    }

    // --- Member handlers ---

    private static void addMember(Library library) {
        System.out.println("\n-- Register New Member --");

        String name  = readNonBlank("Name          : ");
        String email = readNonBlank("Email         : ");

        System.out.print("Phone (optional, press Enter to skip): ");
        String phone = scanner.nextLine().trim();

        try {
            int newId = library.addMember(name, email, phone);
            System.out.println("  ✔ Member registered successfully with ID = " + newId);
        } catch (SQLException e) {
            System.out.println("  ✘ " + e.getMessage());
        }
    }

    private static void listMembers(Library library) {
        System.out.println("\n-- All Members --");
        try {
            List<Member> members = library.listAllMembers();
            if (members.isEmpty()) {
                System.out.println("  No members registered yet.");
            } else {
                members.forEach(m -> System.out.println("  " + m));
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    private static void findMember(Library library) {
        System.out.println("\n-- Find Member by ID --");
        int id = readInt("Member ID: ");

        try {
            Member member = library.findMemberById(id);
            if (member == null) {
                System.out.println("  No member found with ID = " + id + ".");
            } else {
                System.out.println("  " + member);
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    private static void findMemberByEmail(Library library) {
        System.out.println("\n-- Find Member by Email --");
        String email = readNonBlank("Email: ");

        try {
            Member member = library.findMemberByEmail(email);
            if (member == null) {
                System.out.println("  No member found with email \"" + email + "\".");
            } else {
                System.out.println("  " + member);
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    // --- Circulation handlers ---

    private static void issueBook(Library library) {
        System.out.println("\n-- Issue a Book --");
        int bookId   = readPositiveInt("Book ID   : ");
        int memberId = readPositiveInt("Member ID : ");

        try {
            int issueId = library.issueBook(bookId, memberId);
            System.out.println("  ✔ Book successfully issued!");
            System.out.println("  Issue ID : " + issueId);
            System.out.println("  Due date : 14 days from today");
        } catch (SQLException e) {
            System.out.println("  ✘ Cannot issue book: " + e.getMessage());
        }
    }

    private static void returnBook(Library library) {
        System.out.println("\n-- Return an Issued Book --");
        int issueId = readPositiveInt("Issue ID to return: ");

        try {
            library.returnBook(issueId);
            System.out.println("  ✔ Book successfully returned for Issue #" + issueId + "!");
            System.out.println("  Available copy count incremented.");
        } catch (SQLException e) {
            System.out.println("  ✘ Cannot return book: " + e.getMessage());
        }
    }

    // --- Reports handlers ---

    private static void showReportsMenu(Library library) {
        boolean inReports = true;
        while (inReports) {
            System.out.println();
            System.out.println("──── Library Reports & Statistics ────");
            System.out.println("  1. Currently issued books (active loans)");
            System.out.println("  2. Member borrowing history");
            System.out.println("  3. Available books in stock");
            System.out.println("  4. Most borrowed books ranking");
            System.out.println("  5. Summary statistics");
            System.out.println("──────────────────────────────────────");
            System.out.println("  0. Back to main menu");
            System.out.println("──────────────────────────────────────");

            int choice = readInt("Enter choice: ");
            switch (choice) {
                case 1 -> reportCurrentlyIssuedBooks(library);
                case 2 -> reportMemberBorrowingHistory(library);
                case 3 -> reportAvailableBooks(library);
                case 4 -> reportMostBorrowedBooks(library);
                case 5 -> reportSummaryStatistics(library);
                case 0 -> inReports = false;
                default -> System.out.println("  Invalid report option. Please try again.");
            }
        }
    }

    private static void reportCurrentlyIssuedBooks(Library library) {
        System.out.println("\n-- Report 1: All Currently Issued Books --");
        try {
            List<Library.ActiveLoan> loans = library.getCurrentlyIssuedBooks();
            if (loans.isEmpty()) {
                System.out.println("  No books are currently out on loan.");
            } else {
                LocalDate today = LocalDate.now();
                System.out.printf("  %-10s %-30s %-20s %-12s %-12s %-10s%n",
                        "Issue ID", "Book Title", "Member Name", "Issued On", "Due On", "Status");
                System.out.println("  " + "-".repeat(98));
                for (Library.ActiveLoan loan : loans) {
                    String status = (loan.dueOn() != null && loan.dueOn().isBefore(today)) ? "OVERDUE" : "Active";
                    System.out.printf("  %-10d %-30s %-20s %-12s %-12s %-10s%n",
                            loan.issueId(),
                            truncate(loan.bookTitle(), 28),
                            truncate(loan.memberName(), 18),
                            loan.issuedOn(),
                            loan.dueOn(),
                            status);
                }
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error generating report: " + e.getMessage());
        }
    }

    private static void reportMemberBorrowingHistory(Library library) {
        System.out.println("\n-- Report 2: Member Borrowing History --");
        int memberId = readPositiveInt("Enter Member ID: ");

        try {
            Member member = library.findMemberById(memberId);
            if (member == null) {
                System.out.println("  No member found with ID = " + memberId + ".");
                return;
            }

            System.out.println("  Member: " + member.getName() + " (" + member.getEmail() + ")");
            List<Library.BorrowHistory> history = library.getMemberBorrowingHistory(memberId);

            if (history.isEmpty()) {
                System.out.println("  No borrowing history found for this member.");
            } else {
                System.out.printf("  %-10s %-32s %-12s %-12s %-15s%n",
                        "Issue ID", "Book Title", "Issued On", "Due On", "Returned On");
                System.out.println("  " + "-".repeat(85));
                for (Library.BorrowHistory h : history) {
                    String status = h.isReturned() ? h.returnedOn().toString() : "Active (Loan)";
                    System.out.printf("  %-10d %-32s %-12s %-12s %-15s%n",
                            h.issueId(),
                            truncate(h.bookTitle(), 30),
                            h.issuedOn(),
                            h.dueOn(),
                            status);
                }
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error generating report: " + e.getMessage());
        }
    }

    private static void reportAvailableBooks(Library library) {
        System.out.println("\n-- Report 3: Available Books in Stock --");
        try {
            List<Book> books = library.getAvailableBooks();
            if (books.isEmpty()) {
                System.out.println("  No books currently have copies available.");
            } else {
                System.out.println("  Found " + books.size() + " available title(s):");
                books.forEach(b -> System.out.println("  " + b));
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error generating report: " + e.getMessage());
        }
    }

    private static void reportMostBorrowedBooks(Library library) {
        System.out.println("\n-- Report 4: Most Borrowed Books Ranking --");
        try {
            List<Library.BookStat> stats = library.getMostBorrowedBooks();
            if (stats.isEmpty()) {
                System.out.println("  No books in the catalogue to rank.");
            } else {
                System.out.printf("  %-8s %-35s %-25s %-15s%n",
                        "Book ID", "Title", "Author", "Times Borrowed");
                System.out.println("  " + "-".repeat(87));
                for (Library.BookStat s : stats) {
                    System.out.printf("  %-8d %-35s %-25s %-15d%n",
                            s.bookId(),
                            truncate(s.title(), 33),
                            truncate(s.author(), 23),
                            s.borrowCount());
                }
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error generating report: " + e.getMessage());
        }
    }

    private static void reportSummaryStatistics(Library library) {
        System.out.println("\n-- Report 5: Library Summary Statistics --");
        try {
            Library.SummaryStats stats = library.getSummaryStatistics();
            int loanedCopies = stats.totalCopies() - stats.availableCopies();
            System.out.println("  ┌──────────────────────────────────────────┬──────────┐");
            System.out.printf( "  │ %-40s │ %8d │%n", "Total Unique Book Titles", stats.totalBooks());
            System.out.printf( "  │ %-40s │ %8d │%n", "Total Copies in Catalogue", stats.totalCopies());
            System.out.printf( "  │ %-40s │ %8d │%n", "Available Copies on Shelf", stats.availableCopies());
            System.out.printf( "  │ %-40s │ %8d │%n", "Borrowed Copies (Out on Loan)", Math.max(0, loanedCopies));
            System.out.printf( "  │ %-40s │ %8d │%n", "Total Registered Members", stats.totalMembers());
            System.out.printf( "  │ %-40s │ %8d │%n", "Currently Active Loan Records", stats.activeIssues());
            System.out.println("  └──────────────────────────────────────────┴──────────┘");
        } catch (SQLException e) {
            System.out.println("  ✘ Error generating summary statistics: " + e.getMessage());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "N/A";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    // --- Input helpers ---

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a whole number.");
            }
        }
    }

    private static int readPositiveInt(String prompt) {
        while (true) {
            int value = readInt(prompt);
            if (value >= 1) return value;
            System.out.println("  Value must be at least 1.");
        }
    }

    private static String readNonBlank(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isBlank()) return value;
            System.out.println("  This field cannot be empty.");
        }
    }
}
