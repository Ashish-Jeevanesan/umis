package com.umis.mailbox.connector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.umis.common.util.HashUtils;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Parser for Gmail message payloads extracting headers, MIME bodies, and attachments.
 */
@Component
public class GmailPayloadParser {

    /**
     * Extracts plain text and HTML bodies from a Gmail message payload.
     *
     * @param payloadNode JsonNode of the Gmail message payload
     * @return ParsedBody containing decoded plain text and HTML
     */
    public ParsedBody extractBodies(JsonNode payloadNode) {
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder htmlBuilder = new StringBuilder();
        traversePartsForContent(payloadNode, textBuilder, htmlBuilder);
        return new ParsedBody(textBuilder.toString(), htmlBuilder.toString());
    }

    private void traversePartsForContent(
            JsonNode partNode,
            StringBuilder textBuilder,
            StringBuilder htmlBuilder
    ) {
        String mimeType = partNode.path("mimeType").asText("");
        String data = partNode.path("body").path("data").asText("");

        if (!data.isBlank()) {
            try {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(data);
                String content = new String(decodedBytes, StandardCharsets.UTF_8);
                if (mimeType.equalsIgnoreCase("text/plain") && textBuilder.length() == 0) {
                    textBuilder.append(content);
                } else if (mimeType.equalsIgnoreCase("text/html") && htmlBuilder.length() == 0) {
                    htmlBuilder.append(content);
                }
            } catch (Exception ignored) {
                // Ignore decoding errors on non-text bodies
            }
        }

        JsonNode parts = partNode.path("parts");
        if (parts.isArray()) {
            for (JsonNode childPart : parts) {
                traversePartsForContent(childPart, textBuilder, htmlBuilder);
            }
        }
    }

    /**
     * Extracts all attachment references from Gmail payload, capturing both inline data and external attachment IDs.
     *
     * @param payloadNode JsonNode of the Gmail message payload
     * @return list of Gmail attachment references
     */
    public List<GmailAttachmentRef> extractAttachmentReferences(JsonNode payloadNode) {
        List<GmailAttachmentRef> alRefs = new ArrayList<>();
        traversePartsForAttachmentRefs(payloadNode, alRefs);
        return alRefs;
    }

    private void traversePartsForAttachmentRefs(
            JsonNode partNode,
            List<GmailAttachmentRef> alRefs
    ) {
        String filename = partNode.path("filename").asText("");
        String mimeType = partNode.path("mimeType").asText("application/octet-stream");
        String data = partNode.path("body").path("data").asText("");
        String attachmentId = partNode.path("body").path("attachmentId").asText("");
        long size = partNode.path("body").path("size").asLong(0);

        if (!filename.isBlank()) {
            byte[] inlineBytes = null;
            if (!data.isBlank()) {
                try {
                    inlineBytes = Base64.getUrlDecoder().decode(data);
                } catch (Exception ignored) {
                    // Ignore corrupted inline data
                }
            }
            if (inlineBytes != null || !attachmentId.isBlank()) {
                alRefs.add(new GmailAttachmentRef(filename, mimeType, size, attachmentId, inlineBytes));
            }
        }

        JsonNode parts = partNode.path("parts");
        if (parts.isArray()) {
            for (JsonNode childPart : parts) {
                traversePartsForAttachmentRefs(childPart, alRefs);
            }
        }
    }

    /**
     * Extracts inline attachments whose bytes are already present in message parts.
     *
     * @param payloadNode JsonNode of the Gmail message payload
     * @return list of normalized attachments
     */
    public List<NormalizedAttachment> extractInlineAttachments(JsonNode payloadNode) {
        List<NormalizedAttachment> alAttachments = new ArrayList<>();
        traversePartsForAttachments(payloadNode, alAttachments);
        return alAttachments;
    }

    private void traversePartsForAttachments(
            JsonNode partNode,
            List<NormalizedAttachment> alAttachments
    ) {
        String filename = partNode.path("filename").asText("");
        String mimeType = partNode.path("mimeType").asText("application/octet-stream");
        String data = partNode.path("body").path("data").asText("");

        if (!filename.isBlank() && !data.isBlank()) {
            try {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(data);
                String fileHash = HashUtils.sha256Hex(decodedBytes);
                alAttachments.add(NormalizedAttachment.of(
                        filename, mimeType, decodedBytes.length, fileHash, decodedBytes));
            } catch (Exception ignored) {
                // Ignore corrupted attachment data
            }
        }

        JsonNode parts = partNode.path("parts");
        if (parts.isArray()) {
            for (JsonNode childPart : parts) {
                traversePartsForAttachments(childPart, alAttachments);
            }
        }
    }

    public record ParsedBody(String text, String html) {}

    public record GmailAttachmentRef(
            String filename,
            String mimeType,
            long size,
            String attachmentId,
            byte[] inlineData
    ) {}
}
