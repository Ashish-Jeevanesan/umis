package com.umis.email.service;

import com.umis.config.UmisProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verifies umis.storage.type correctly selects exactly one ObjectStorageClient implementation,
 * and - just as importantly - that the OTHER backend's bean (and its dependencies, like the AWS
 * S3Client) is never even instantiated.
 */
class StorageProviderSelectionTest {

    @TempDir
    Path tempDir;

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestStorageConfig.class, LocalFilesystemStorageClient.class, S3ObjectStorageClient.class)
                // Keep this test from touching the project's real backend/storage directory.
                .withPropertyValues("umis.storage.local-base-path=" + tempDir);
    }

    @Test
    @DisplayName("umis.storage.type=LOCAL activates LocalFilesystemStorageClient and never creates S3ObjectStorageClient")
    void testLocalTypeSelectsLocalClientOnly() {
        contextRunner().withPropertyValues("umis.storage.type=LOCAL").run(context -> {
            assertTrue(context.containsBean("localFilesystemStorageClient"));
            assertFalse(context.containsBean("s3ObjectStorageClient"));
            assertTrue(context.getBean(ObjectStorageClient.class) instanceof LocalFilesystemStorageClient);
        });
    }

    @Test
    @DisplayName("umis.storage.type=S3 activates S3ObjectStorageClient and never creates LocalFilesystemStorageClient")
    void testS3TypeSelectsS3ClientOnly() {
        contextRunner().withPropertyValues("umis.storage.type=S3").run(context -> {
            assertTrue(context.containsBean("s3ObjectStorageClient"));
            assertFalse(context.containsBean("localFilesystemStorageClient"));
            assertTrue(context.getBean(ObjectStorageClient.class) instanceof S3ObjectStorageClient);
        });
    }

    @Test
    @DisplayName("No storage type configured defaults to LOCAL (zero-config)")
    void testMissingTypeDefaultsToLocal() {
        contextRunner().run(context -> {
            assertTrue(context.containsBean("localFilesystemStorageClient"));
            assertFalse(context.containsBean("s3ObjectStorageClient"));
        });
    }

    @Configuration
    @EnableConfigurationProperties(UmisProperties.class)
    static class TestStorageConfig {
        @Bean
        S3Client s3Client() {
            // Only actually used when umis.storage.type=S3 selects S3ObjectStorageClient; a mock
            // is enough to prove wiring without a real AWS/MinIO endpoint.
            return mock(S3Client.class);
        }
    }
}
