package com.po.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main application entry point for the PO Processor microservice.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class PoProcessorApplication {

    public static void main(String[] args) {
        loadDotEnvIfExists();
        SpringApplication.run(PoProcessorApplication.class, args);
    }

    /**
     * Loads environment variables from local .env file as system properties
     * if not already present in the host environment.
     */
    static void loadDotEnvIfExists() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            return;
        }

        try {
            List<String> fileLines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String rawLine : fileLines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                String envKey = line.substring(0, separatorIndex).trim();
                String envValue = line.substring(separatorIndex + 1).trim();

                if ((envValue.startsWith("\"") && envValue.endsWith("\""))
                        || (envValue.startsWith("'") && envValue.endsWith("'"))) {
                    envValue = envValue.substring(1, envValue.length() - 1);
                }

                System.setProperty(envKey, envValue);
            }
        } catch (Exception loadException) {
            System.err.println("Notice: Could not load .env file: " + loadException.getMessage());
        }
    }
}
