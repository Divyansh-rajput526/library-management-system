package com.library;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Library manages the JDBC connection and will hold all database operations.
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
        System.out.println("Connection established successfully.");
    }

    // -------------------------------------------------------------------------
    // Phase 1 – connection test only
    // -------------------------------------------------------------------------

    /**
     * Prints basic metadata about the connected PostgreSQL server.
     * Demonstrates that JDBC is working before we build real features.
     */
    public void testConnection() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        System.out.println("Database product : " + meta.getDatabaseProductName());
        System.out.println("Database version : " + meta.getDatabaseProductVersion());
        System.out.println("JDBC driver      : " + meta.getDriverName()
                           + " " + meta.getDriverVersion());
        System.out.println("Connection test  : PASSED");
    }

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

