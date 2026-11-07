package com.umis.email.service;

import com.umis.common.exception.MailboxIntegrationException;
import com.umis.config.UmisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFilesystemStorageClientTest {

    @TempDir
    Path tempDir;

    private LocalFilesystemStorageClient client;

    @BeforeEach
    void setUp() {
        UmisProperties properties = new UmisProperties();
        properties.getStorage().setLocalBasePath(tempDir.toString());
        client = new LocalFilesystemStorageClient(properties);
        client.ensureBaseDirectoryExists();
    }

    @Test
    @DisplayName("Round-trips a reasonably large payload via streaming put/get")
    void testPutAndGetRoundTrip() throws IOException {
        // Large enough to be meaningfully more than a trivial in-memory buffer, to exercise the
        // streaming read/write path rather than a single-chunk copy.
        byte[] content = new byte[5 * 1024 * 1024];
        new java.util.Random(42).nextBytes(content);
        String key = "emails/tenant-a/mailbox-1/email-1/body.txt";

        client.putObject(key, new ByteArrayInputStream(content), content.length, "text/plain");

        ObjectStorageClient.DownloadedObject downloaded = client.getObject(key);
        byte[] readBack = downloaded.inputStream().readAllBytes();
        downloaded.inputStream().close();

        assertArrayEquals(content, readBack);
        assertEquals(content.length, downloaded.contentLength());
    }

    @Test
    @DisplayName("Different tenants never collide and resolve to isolated directory trees")
    void testTenantDirectoryIsolation() throws IOException {
        String keyTenantA = "emails/tenant-a/mailbox-1/email-1/body.txt";
        String keyTenantB = "emails/tenant-b/mailbox-1/email-1/body.txt";

        client.putObject(keyTenantA, "tenant A content".getBytes(StandardCharsets.UTF_8), "text/plain");
        client.putObject(keyTenantB, "tenant B content".getBytes(StandardCharsets.UTF_8), "text/plain");

        String readA = new String(client.getObject(keyTenantA).inputStream().readAllBytes(), StandardCharsets.UTF_8);
        String readB = new String(client.getObject(keyTenantB).inputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals("tenant A content", readA);
        assertEquals("tenant B content", readB);
        assertTrue(Files.exists(tempDir.resolve("emails/tenant-a")));
        assertTrue(Files.exists(tempDir.resolve("emails/tenant-b")));
    }

    @Test
    @DisplayName("Rejects a key that attempts to traverse outside the base directory")
    void testPathTraversalRejected() {
        String maliciousKey = "../../../../etc/passwd";

        assertThrows(IllegalArgumentException.class, () ->
                client.putObject(maliciousKey, "payload".getBytes(StandardCharsets.UTF_8), "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> client.getObject(maliciousKey));

        // Confirm nothing was written anywhere outside the sandboxed temp directory.
        Path outside = tempDir.getParent().resolve("passwd");
        assertFalse(Files.exists(outside));
    }

    @Test
    @DisplayName("Rejects a Windows-style traversal attempt too")
    void testWindowsStyleTraversalRejected() {
        String maliciousKey = "attachments\\..\\..\\..\\Windows\\System32\\evil.txt";
        assertThrows(IllegalArgumentException.class, () ->
                client.putObject(maliciousKey, "payload".getBytes(StandardCharsets.UTF_8), "text/plain"));
    }

    @Test
    @DisplayName("A failed/interrupted write leaves no partial file at the target key and cleans up its temp file")
    void testInterruptedWriteLeavesNoPartialFile() {
        String key = "attachments/tenant-a/email-1/att-1/content";
        InputStream explodingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated network interruption mid-upload");
            }
        };

        client.putObject(key, explodingStream, 100, "application/octet-stream");

        Path target = tempDir.resolve(key);
        assertFalse(Files.exists(target), "No partial file should exist at the target key after a failed write");

        // No stray temp files left behind in the parent directory either.
        try (Stream<Path> siblings = Files.exists(target.getParent())
                ? Files.list(target.getParent())
                : Stream.<Path>empty()) {
            List<String> leftoverTempFiles = siblings
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(".upload-"))
                    .collect(Collectors.toList());
            assertTrue(leftoverTempFiles.isEmpty(), "No leftover temp files expected, found: " + leftoverTempFiles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Requesting an object that was never stored throws a clear error instead of returning empty content")
    void testGetMissingObjectThrows() {
        assertThrows(MailboxIntegrationException.class, () -> client.getObject("emails/tenant-a/mailbox-1/does-not-exist/body.txt"));
    }
}
