package com.example.hr_management_backend.core.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Production-grade, zero-dependency .env loader for Spring Boot.
 * Reads environment variables from the local .env file before the Spring ApplicationContext starts,
 * ensuring seamless resolution across Spring Boot @Value, environment placeholders, and test suites
 * without leaking secrets into git-tracked configuration files.
 */
public final class DotenvLoader {

    private static final Logger log = Logger.getLogger(DotenvLoader.class.getName());

    private DotenvLoader() {}

    public static void load() {
        File envFile = findEnvFile();
        if (envFile == null || !envFile.exists() || !envFile.canRead()) {
            log.info("[DotenvLoader] No local .env file found. Falling back to OS environment variables.");
            return;
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Ignore empty lines and comments
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }

                int eqIdx = line.indexOf('=');
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();

                // Strip surrounding single or double quotes
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) ||
                    (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1);
                }

                // OS environment and existing JVM system properties take precedence (Twelve-Factor App rule)
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                    count++;
                }
            }
            log.info(String.format("[DotenvLoader] Successfully loaded %d environment variable(s) from %s", count, envFile.getAbsolutePath()));
        } catch (IOException e) {
            log.warning("[DotenvLoader] Error reading .env file: " + e.getMessage());
        }
    }

    private static File findEnvFile() {
        // 1. Current working directory
        File direct = new File(".env");
        if (direct.exists()) return direct;

        // 2. Based on user.dir
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            File userDirFile = new File(userDir, ".env");
            if (userDirFile.exists()) return userDirFile;

            // Check parent folder if running inside a submodule directory
            File parentDirFile = new File(new File(userDir).getParentFile(), ".env");
            if (parentDirFile.exists()) return parentDirFile;
        }

        return null;
    }
}
