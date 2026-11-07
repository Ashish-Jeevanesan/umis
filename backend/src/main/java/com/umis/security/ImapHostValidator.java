package com.umis.security;

import com.umis.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Validator preventing Server-Side Request Forgery (SSRF) when configuring IMAP hosts.
 * Rejects loopback, private, link-local, and cloud metadata IP addresses.
 */
@Component
public class ImapHostValidator {

    private static final Logger log = LoggerFactory.getLogger(ImapHostValidator.class);

    @Value("${umis.security.allow-local-imap-hosts:false}")
    private boolean allowLocalImapHosts;

    /**
     * Validates an IMAP host name or IP address.
     *
     * @param strHost target host name or IP string
     * @throws BadRequestException if the host resolves to a prohibited internal IP address
     */
    public void validateHost(String strHost) {
        if (strHost == null || strHost.isBlank()) {
            throw new BadRequestException("IMAP server host cannot be empty");
        }

        String strCleanHost = strHost.trim().toLowerCase();

        // If local test hosts are permitted (e.g., test environment with GreenMail), allow localhost
        if (allowLocalImapHosts) {
            log.debug("Local IMAP hosts permitted by configuration for host: {}", strCleanHost);
            return;
        }

        // Block well-known loopback and cloud metadata hostnames
        if (strCleanHost.equals("localhost")
                || strCleanHost.endsWith(".localhost")
                || strCleanHost.equals("metadata.google.internal")
                || strCleanHost.equals("instance-data")) {
            throw new BadRequestException("Prohibited IMAP server host: Access to local or metadata host is blocked");
        }

        try {
            InetAddress[] inetAddresses = InetAddress.getAllByName(strCleanHost);
            for (InetAddress address : inetAddresses) {
                checkProhibitedAddress(address);
            }
        } catch (UnknownHostException hostException) {
            throw new BadRequestException("Unable to resolve IMAP host: " + strCleanHost);
        }
    }

    private void checkProhibitedAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            throw new BadRequestException("Prohibited IMAP server host: Loopback addresses are blocked");
        }
        if (address.isSiteLocalAddress()) {
            throw new BadRequestException("Prohibited IMAP server host: Private RFC 1918 networks are blocked");
        }
        // Check AWS / GCP / Azure link-local metadata address (169.254.169.254)
        String strIpAddress = address.getHostAddress();
        if (strIpAddress.startsWith("169.254.") || strIpAddress.startsWith("fe80:")) {
            throw new BadRequestException("Prohibited IMAP server host: Cloud metadata IP is blocked");
        }
        if (address.isLinkLocalAddress()) {
            throw new BadRequestException("Prohibited IMAP server host: Link-local addresses are blocked");
        }
        if (address.isMulticastAddress()) {
            throw new BadRequestException("Prohibited IMAP server host: Multicast addresses are blocked");
        }
    }
}
