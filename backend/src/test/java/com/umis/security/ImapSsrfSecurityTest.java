package com.umis.security;

import com.umis.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImapSsrfSecurityTest {

    private ImapHostValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImapHostValidator();
        ReflectionTestUtils.setField(validator, "allowLocalImapHosts", false);
    }

    @Test
    @DisplayName("SSRF Validator blocks loopback localhost addresses")
    void testBlocksLocalhost() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> validator.validateHost("localhost")
        );
        assertTrue(exception.getMessage().contains("blocked"));
    }

    @Test
    @DisplayName("SSRF Validator blocks IPv4 loopback 127.0.0.1")
    void testBlocksLoopbackIp() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> validator.validateHost("127.0.0.1")
        );
        assertTrue(exception.getMessage().contains("Loopback"));
    }

    @Test
    @DisplayName("SSRF Validator blocks cloud metadata address 169.254.169.254")
    void testBlocksCloudMetadata() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> validator.validateHost("169.254.169.254")
        );
        assertTrue(exception.getMessage().contains("metadata"));
    }

    @Test
    @DisplayName("SSRF Validator blocks Google Cloud internal metadata hostname")
    void testBlocksGoogleMetadataHostname() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> validator.validateHost("metadata.google.internal")
        );
        assertTrue(exception.getMessage().contains("blocked"));
    }

    @Test
    @DisplayName("SSRF Validator blocks private RFC 1918 Class A network 10.0.0.1")
    void testBlocksPrivateClassA() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> validator.validateHost("10.0.0.1")
        );
        assertTrue(exception.getMessage().contains("RFC 1918"));
    }

    @Test
    @DisplayName("SSRF Validator blocks private RFC 1918 Class C network 192.168.1.1")
    void testBlocksPrivateClassC() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> validator.validateHost("192.168.1.1")
        );
        assertTrue(exception.getMessage().contains("RFC 1918"));
    }

    @Test
    @DisplayName("SSRF Validator blocks empty or null hosts")
    void testBlocksEmptyHost() {
        assertThrows(BadRequestException.class, () -> validator.validateHost(""));
        assertThrows(BadRequestException.class, () -> validator.validateHost("   "));
        assertThrows(BadRequestException.class, () -> validator.validateHost(null));
    }

    @Test
    @DisplayName("SSRF Validator permits localhost when allowLocalImapHosts flag is enabled")
    void testPermitsLocalhostWhenFlagEnabled() {
        ReflectionTestUtils.setField(validator, "allowLocalImapHosts", true);
        assertDoesNotThrow(() -> validator.validateHost("localhost"));
        assertDoesNotThrow(() -> validator.validateHost("127.0.0.1"));
    }
}
