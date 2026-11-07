package com.umis.dashboard.dto;

import com.umis.mailbox.model.MailboxProvider;

/**
 * Mailbox provider distribution breakdown.
 */
public record ProviderDistributionDto(
        MailboxProvider provider,
        String displayName,
        long mailboxCount,
        long emailsIngested
) {}
