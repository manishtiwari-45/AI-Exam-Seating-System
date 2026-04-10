package com.exam.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DBConnection — centralized database access using HikariCP connection pool.
 *
 * WHY HIKARICP OVER RAW DRIVERMANAGER:
 *   - DriverManager.getConnection() opens a NEW TCP connection on every call.
 *     Under load, this exhausts MySQL's connection limit fast.
 *   - HikariCP maintains a warm pool of N connections and reuses them.
 *     Borrowing a connection from the pool takes ~0.1ms vs ~50ms for a new one.
 *
 * CONFIG PRIORITY (highest to lowest):
 *   1. Environment variable (e.g., DB_HOST set in Railway dashboard)
 *   2. application.properties (local dev defaults)
 *
 * This means you never change production credentials in code.
 * Just set env vars on the deployment platform.
 */
public class DBConnection {

    // Single pool instance — created once at class load, shared by all threads.
    private static final HikariDataSource pool;

    static {
        Properties props = loadProperties();
        pool = buildPool(props);
    }

    /**
     * Borrow a connection from the pool.
     * ALWAYS use in a try-with-resources block so it's returned automatically:
     *
     *   try (Connection c = DBConnection.getConnection()) {
     *       // use c
     *   }  // <-- connection auto-returned to pool here, NOT closed
     */
    public static Connection getConnection() throws SQLException {
        return pool.getConnection();
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    /**
     * Loads application.properties from the classpath.
     * File must be at: src/main/resources/application.properties
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (in != null) {
                props.load(in);
            } else {
                System.err.println("[DBConnection] WARNING: application.properties not found on classpath. " +
                        "Falling back to environment variables only.");
            }
        } catch (IOException e) {
            System.err.println("[DBConnection] Failed to read application.properties: " + e.getMessage());
        }
        return props;
    }

    /**
     * Reads a config value.
     * Environment variable wins over application.properties.
     * This is the key mechanism that makes local dev vs production seamless.
     */
    private static String get(Properties props, String key, String fallback) {
        // 1. Check env variable first
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.isBlank()) return envVal;

        // 2. Check properties file
        String propVal = props.getProperty(key);
        if (propVal != null && !propVal.isBlank()) return propVal;

        // 3. Use hardcoded fallback
        return fallback;
    }

    /**
     * Builds the HikariCP pool from resolved config.
     */
    private static HikariDataSource buildPool(Properties props) {
        String host    = get(props, "DB_HOST",      "localhost");
        String port    = get(props, "DB_PORT",      "3306");
        String name    = get(props, "DB_NAME",      "exam_seating_db");
        String user    = get(props, "DB_USER",      "root");
        String pass    = get(props, "DB_PASS",      "");
        int    poolSz  = Integer.parseInt(get(props, "DB_POOL_SIZE", "5"));
        long   timeout = Long.parseLong(get(props, "DB_TIMEOUT_MS", "30000"));

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, name
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(poolSz);
        config.setConnectionTimeout(timeout);
        config.setPoolName("ExamSeatingPool");

        // Test query run on each borrowed connection to detect stale connections
        config.setConnectionTestQuery("SELECT 1");

        System.out.println("[DBConnection] Pool initialized → " + jdbcUrl + " (pool size: " + poolSz + ")");

        return new HikariDataSource(config);
    }
}