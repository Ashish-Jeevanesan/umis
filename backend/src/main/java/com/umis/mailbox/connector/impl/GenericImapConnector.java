package com.umis.mailbox.connector.impl;

import com.umis.common.exception.MailboxIntegrationException;
import com.umis.common.util.HashUtils;
import com.umis.mailbox.connector.MailboxConnector;
import com.umis.mailbox.connector.model.ConnectionResult;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.connector.model.ProviderCapability;
import com.umis.mailbox.connector.model.ProviderConnectionInit;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.security.EncryptionService;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Generic IMAP adapter supporting standard TLS/SSL IMAP servers and UID-based synchronization.
 */
@Component
public class GenericImapConnector implements MailboxConnector {

    private static final Logger log = LoggerFactory.getLogger(GenericImapConnector.class);

    private final EncryptionService encryptionService;
    private final com.umis.security.ImapHostValidator imapHostValidator;

    public GenericImapConnector(
            EncryptionService encryptionService,
            com.umis.security.ImapHostValidator imapHostValidator
    ) {
        this.encryptionService = encryptionService;
        this.imapHostValidator = imapHostValidator;
    }

    @Override
    public MailboxProvider getProvider() {
        return MailboxProvider.GENERIC_IMAP;
    }

    @Override
    public ProviderCapability getCapability() {
        return new ProviderCapability(
                MailboxProvider.GENERIC_IMAP,
                "Generic IMAP",
                "DIRECT_CREDENTIALS",
                "PASSWORD_OR_APP_PASSWORD",
                List.of("READ_MESSAGES", "ATTACHMENTS", "UID_INCREMENTAL_SYNC", "POLLING"),
                false,
                true
        );
    }

    @Override
    public ProviderConnectionInit initiateConnection(String redirectUri, String state) {
        throw new UnsupportedOperationException("IMAP uses direct server credentials, not OAuth redirection");
    }

    @Override
    public ConnectionResult completeConnection(String authorizationCode, String codeVerifier, String redirectUri) {
        throw new UnsupportedOperationException("IMAP uses direct server credentials, not OAuth code exchange");
    }

    @Override
    public boolean refreshCredentials(MailboxCredentials credentials) {
        return true; // Password/App password credentials do not require token refresh
    }

    @Override
    public boolean testConnection(MailboxConnection connection, MailboxCredentials credentials) {
        try (Store store = openStore(credentials)) {
            return store.isConnected();
        } catch (Exception exception) {
            log.warn("IMAP connection test failed for {}: {}", connection.getEmailAddress(), exception.getMessage());
            return false;
        }
    }

    @Override
    public FetchResult fetchMessages(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String cursor,
            int maxResults
    ) {
        long lastUid = cursor != null && !cursor.isBlank() ? Long.parseLong(cursor) : 0L;
        List<NormalizedEmail> normalizedEmails = new ArrayList<>();
        long highestUidSeen = lastUid;

        try (Store store = openStore(credentials)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            if (inbox instanceof UIDFolder uidFolder) {
                Message[] messages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);

                for (int index = 0; index < messages.length; index++) {
                    Message msg = messages[index];
                    long uid = uidFolder.getUID(msg);
                    if (uid > lastUid) {
                        if (uid > highestUidSeen) {
                            highestUidSeen = uid;
                        }
                        normalizedEmails.add(normalizeMimeMessage(connection, msg, String.valueOf(uid)));
                        if (maxResults > 0 && normalizedEmails.size() >= maxResults) {
                            break;
                        }
                    }
                }
            }
            inbox.close(false);
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Failed to fetch IMAP messages: " + exception.getMessage(), exception);
        }

        String nextCursor = String.valueOf(highestUidSeen);
        return new FetchResult(normalizedEmails, nextCursor, false, null, highestUidSeen);
    }

    @Override
    public NormalizedEmail fetchMessage(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    ) {
        try (Store store = openStore(credentials)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            if (inbox instanceof UIDFolder uidFolder) {
                Message message = uidFolder.getMessageByUID(Long.parseLong(providerMessageId));
                if (message != null) {
                    NormalizedEmail normalized = normalizeMimeMessage(connection, message, providerMessageId);
                    inbox.close(false);
                    return normalized;
                }
            }
            inbox.close(false);
            throw new MailboxIntegrationException("Message not found with UID: " + providerMessageId);
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Failed to fetch IMAP message: " + exception.getMessage(), exception);
        }
    }

    @Override
    public List<NormalizedAttachment> fetchAttachments(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    ) {
        NormalizedEmail email = fetchMessage(connection, credentials, providerMessageId);
        return email.attachments();
    }

    @Override
    public String startMonitoring(MailboxConnection connection, MailboxCredentials credentials, String webhookUrl) {
        return null; // IMAP relies on scheduled polling fallback or IDLE
    }

    @Override
    public boolean stopMonitoring(MailboxConnection connection, MailboxCredentials credentials, String subscriptionId) {
        return true;
    }

    @Override
    public void disconnect(MailboxConnection connection, MailboxCredentials credentials) {
        // Direct credentials invalidated by removing record
    }

    private Store openStore(MailboxCredentials credentials) throws Exception {
        imapHostValidator.validateHost(credentials.getServerHost());

        Properties properties = new Properties();
        String protocol = credentials.isUseSsl() ? "imaps" : "imap";
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", credentials.getServerHost());
        properties.put("mail." + protocol + ".port", String.valueOf(credentials.getServerPort()));
        properties.put("mail." + protocol + ".ssl.enable", String.valueOf(credentials.isUseSsl()));
        properties.put("mail." + protocol + ".timeout", "10000");
        properties.put("mail." + protocol + ".connectiontimeout", "10000");

        Session session = Session.getInstance(properties);
        Store store = session.getStore(protocol);
        String decryptedPassword = encryptionService.decrypt(credentials.getEncryptedSecret());
        store.connect(
                credentials.getServerHost(),
                credentials.getServerPort() != null ? credentials.getServerPort() : (credentials.isUseSsl() ? 993 : 143),
                credentials.getMailbox().getEmailAddress(),
                decryptedPassword
        );
        return store;
    }

    private NormalizedEmail normalizeMimeMessage(
            MailboxConnection connection,
            Message message,
            String uid
    ) throws Exception {
        String subject = message.getSubject() != null ? message.getSubject() : "(No Subject)";
        Address[] senders = message.getFrom();
        String sender = (senders != null && senders.length > 0) ? senders[0].toString() : "unknown@unknown.com";
        List<String> recipients = message.getAllRecipients() != null
                ? Arrays.stream(message.getAllRecipients()).map(Address::toString).collect(Collectors.toList())
                : List.of(connection.getEmailAddress());

        Instant receivedAt = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant()
                : (message.getSentDate() != null ? message.getSentDate().toInstant() : Instant.now());

        StringBuilder textBuilder = new StringBuilder();
        StringBuilder htmlBuilder = new StringBuilder();
        List<NormalizedAttachment> attachments = new ArrayList<>();

        extractContentAndAttachments(message, textBuilder, htmlBuilder, attachments);

        String bodyText = textBuilder.toString();
        String bodyHtml = htmlBuilder.toString();
        String contentHash = HashUtils.sha256Hex(connection.getId() + ":" + uid + ":" + subject);

        return NormalizedEmail.create(
                connection.getTenantId(),
                connection.getId(),
                MailboxProvider.GENERIC_IMAP,
                uid,
                sender,
                sender,
                recipients,
                subject,
                bodyText,
                bodyHtml,
                receivedAt,
                attachments,
                contentHash
        );
    }

    private void extractContentAndAttachments(
            Part part,
            StringBuilder textBuilder,
            StringBuilder htmlBuilder,
            List<NormalizedAttachment> attachments
    ) throws Exception {
        if (part.isMimeType("text/plain")) {
            textBuilder.append(part.getContent().toString());
        } else if (part.isMimeType("text/html")) {
            htmlBuilder.append(part.getContent().toString());
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                extractContentAndAttachments(multipart.getBodyPart(index), textBuilder, htmlBuilder, attachments);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            String fileName = part.getFileName() != null ? part.getFileName() : "attachment.bin";
            try (InputStream stream = part.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                stream.transferTo(out);
                byte[] bytes = out.toByteArray();
                String hash = HashUtils.sha256Hex(bytes);
                attachments.add(NormalizedAttachment.of(fileName, part.getContentType(), bytes.length, hash, bytes));
            }
        }
    }
}
