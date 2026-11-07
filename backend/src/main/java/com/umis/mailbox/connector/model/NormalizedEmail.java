package com.umis.mailbox.connector.model;

import com.umis.mailbox.model.MailboxProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Normalized provider-independent representation of an ingested email message.
 */
public record NormalizedEmail(
        UUID emailId,
        UUID tenantId,
        UUID mailboxId,
        MailboxProvider provider,
        String providerMessageId,
        String threadId,
        String internetMessageId,
        String sender,
        String senderName,
        List<String> recipients,
        List<String> cc,
        List<String> bcc,
        String subject,
        String snippet,
        String bodyText,
        String bodyHtml,
        boolean bodyHtmlAvailable,
        Instant receivedAt,
        List<NormalizedAttachment> attachments,
        String contentHash
) {
    public static NormalizedEmail create(
            UUID tenantId,
            UUID mailboxId,
            MailboxProvider provider,
            String providerMessageId,
            String sender,
            String senderName,
            List<String> recipients,
            String subject,
            String bodyText,
            String bodyHtml,
            Instant receivedAt,
            List<NormalizedAttachment> attachments,
            String contentHash
    ) {
        return new NormalizedEmail(
                UUID.randomUUID(),
                tenantId,
                mailboxId,
                provider,
                providerMessageId,
                null,
                null,
                sender,
                senderName,
                recipients != null ? recipients : List.of(),
                List.of(),
                List.of(),
                subject,
                bodyText != null && bodyText.length() > 200 ? bodyText.substring(0, 200) : bodyText,
                bodyText,
                bodyHtml,
                bodyHtml != null && !bodyHtml.isBlank(),
                receivedAt != null ? receivedAt : Instant.now(),
                attachments != null ? attachments : List.of(),
                contentHash
        );
    }
}
