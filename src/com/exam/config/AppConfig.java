package com.exam.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * AppConfig — single access point for all non-DB application config.
 *
 * Reads from application.properties but environment variables always win.
 * Used by SimpleWebServer (PORT) and AIAnalysisService (ANTHROPIC_API_KEY).
 *
 * Usage:
 *   int port = AppConfig.getInt("PORT", 8080);
 *   String key = AppConfig.get("ANTHROPIC_API_KEY", "");
 */
public class AppConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = AppConfig.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            System.err.println("[AppConfig] Could not load application.properties");
        }
    }

    /**
     * Get a string config value.
     * Env variable > application.properties > fallback.
     */
    public static String get(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        String prop = props.getProperty(key);
        if (prop != null && !prop.isBlank()) return prop;
        return fallback;
    }

    /**
     * Get an integer config value.
     */
    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}