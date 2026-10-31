package com.library;

/**
 * Application entry point.
 *
 * Phase 1: test the database connection only.
 * Future phases will add a menu and full CRUD operations.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== Library Management System ===");

        // Library wraps the JDBC connection; try-with-resources closes it automatically.
        try (Library library = new Library()) {
            library.testConnection();
        } catch (Exception e) {
            System.err.println("Failed to start: " + e.getMessage());
        }
    }
}

