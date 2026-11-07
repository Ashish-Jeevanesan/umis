package com.umis.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umis.email.model.TriggerType;
import com.umis.email.service.EmailSyncManager;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxSubscription;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.mailbox.repository.MailboxSubscriptionRepository;
import com.umis.webhook.model.WebhookEvent;
import com.umis.webhook.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service processing incoming provider webhook payloads asynchronously.
 */
@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final WebhookEventRepository webhookRepository;
    private final MailboxSubscriptionRepository subscriptionRepository;
    private final MailboxConnectionRepository connectionRepository;
    private final EmailSyncManager syncManager;
    private final ObjectMapper objectMapper;

    public WebhookProcessingService(
            WebhookEventRepository webhookRepository,
            MailboxSubscriptionRepository subscriptionRepository,
            MailboxConnectionRepository connectionRepository,
            EmailSyncManager syncManager,
            ObjectMapper objectMapper
    ) {
        this.webhookRepository = webhookRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.connectionRepository = connectionRepository;
        this.syncManager = syncManager;
        this.objectMapper = objectMapper;
    }

    public void processMicrosoftWebhook(String payload, String headers) {
        WebhookEvent event = new WebhookEvent("MICROSOFT_365", null, "change_notification", payload, headers);
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode valueArray = root.path("value");
            for (JsonNode item : valueArray) {
                String subId = item.path("subscriptionId").asText();
                String clientState = item.path("clientState").asText();

                Optional<MailboxSubscription> subOpt = subscriptionRepository.findBySubscriptionId(subId);
                if (subOpt.isPresent()) {
                    MailboxSubscription sub = subOpt.get();
                    if (sub.getClientState() != null && !sub.getClientState().equals(clientState)) {
                        log.warn("Invalid clientState in Microsoft webhook for subscription {}", subId);
                        event.setStatus("REJECTED_AUTHENTICITY");
                        continue;
                    }
                    MailboxConnection mailbox = sub.getMailbox();
                    event.setMailbox(mailbox);
                    syncManager.synchronizeMailboxAsync(mailbox.getId(), TriggerType.WEBHOOK);
                } else {
                    log.warn("Unknown or unregistered subscription ID in Microsoft webhook: {}", subId);
                    event.setStatus("UNKNOWN_SUBSCRIPTION");
                }
            }
        } catch (Exception exception) {
            log.warn("Error parsing Microsoft webhook: {}", exception.getMessage());
            event.setErrorDetails(exception.getMessage());
            event.setStatus("FAILED");
        }
        webhookRepository.save(event);
    }

    public void processGoogleWebhook(String payload, String headers) {
        WebhookEvent event = new WebhookEvent("GMAIL", null, "cloud_pubsub", payload, headers);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String dataBase64 = root.path("message").path("data").asText();
            if (dataBase64 != null && !dataBase64.isBlank()) {
                byte[] decoded = java.util.Base64.getDecoder().decode(dataBase64);
                JsonNode msgData = objectMapper.readTree(decoded);
                String emailAddress = msgData.path("emailAddress").asText();

                connectionRepository.findByProvider(com.umis.mailbox.model.MailboxProvider.GMAIL).stream()
                        .filter(m -> m.getEmailAddress().equalsIgnoreCase(emailAddress))
                        .findFirst()
                        .ifPresent(mailbox -> {
                            event.setMailbox(mailbox);
                            syncManager.synchronizeMailboxAsync(mailbox.getId(), TriggerType.WEBHOOK);
                        });
            }
        } catch (Exception exception) {
            log.warn("Error parsing Google Pub/Sub webhook: {}", exception.getMessage());
            event.setErrorDetails(exception.getMessage());
            event.setStatus("FAILED");
        }
        webhookRepository.save(event);
    }

    public void processUnifiedWebhook(String payload, String headers) {
        WebhookEvent event = new WebhookEvent("UNIFIED_API", null, "notification", payload, headers);
        webhookRepository.save(event);
    }
}
