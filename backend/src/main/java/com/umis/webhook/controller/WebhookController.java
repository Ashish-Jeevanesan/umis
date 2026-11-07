package com.umis.webhook.controller;

import com.umis.webhook.service.WebhookProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook receiver controller handling incoming push notifications from email providers.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Provider webhook listener endpoints")
public class WebhookController {

    private final WebhookProcessingService webhookService;

    public WebhookController(WebhookProcessingService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/microsoft", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Microsoft Graph change notification webhook receiver")
    public ResponseEntity<String> handleMicrosoftWebhook(
            @RequestParam(required = false) String validationToken,
            @RequestBody(required = false) String payload,
            @RequestHeader Map<String, String> headers
    ) {
        // Microsoft Graph subscription validation handshake
        if (validationToken != null && !validationToken.isBlank()) {
            return ResponseEntity.ok(validationToken);
        }
        if (payload != null) {
            webhookService.processMicrosoftWebhook(payload, headers.toString());
        }
        return ResponseEntity.accepted().body("ACCEPTED");
    }

    @PostMapping("/google")
    @Operation(summary = "Google Cloud Pub/Sub Gmail watch webhook receiver")
    public ResponseEntity<String> handleGoogleWebhook(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers
    ) {
        webhookService.processGoogleWebhook(payload, headers.toString());
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/unified-provider")
    @Operation(summary = "Unified email API webhook receiver")
    public ResponseEntity<String> handleUnifiedWebhook(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers
    ) {
        webhookService.processUnifiedWebhook(payload, headers.toString());
        return ResponseEntity.ok("OK");
    }
}
