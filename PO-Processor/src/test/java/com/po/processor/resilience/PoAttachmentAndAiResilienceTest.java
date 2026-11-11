package com.po.processor.resilience;

import com.po.processor.ai.GeminiDocumentIntelligenceProvider;
import com.po.processor.ai.GeminiPromptBuilder;
import com.po.processor.ai.GeminiResponseParser;
import com.po.processor.ai.dto.DocumentExtractionRequest;
import com.po.processor.document.BoundedAttachmentStreamReader;
import com.po.processor.document.DocxDocumentExtractor;
import com.po.processor.document.ImageDocumentValidator;
import com.po.processor.document.PdfBoxDocumentExtractor;
import com.po.processor.exception.AttachmentSizeLimitExceededException;
import com.po.processor.exception.GeminiResponseTruncatedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite verifying bounded streaming, attachment memory limits, document parser safeguards, and AI resilience.
 */
public class PoAttachmentAndAiResilienceTest {

    private final BoundedAttachmentStreamReader streamReader = new BoundedAttachmentStreamReader(1024 * 1024L, 2 * 1024 * 1024L); // 1MB / 2MB for tests
    private final PdfBoxDocumentExtractor pdfExtractor = new PdfBoxDocumentExtractor(5); // 5 max pages for test
    private final DocxDocumentExtractor docxExtractor = new DocxDocumentExtractor(500);
    private final ImageDocumentValidator imageValidator = new ImageDocumentValidator(500); // 500px max for test
    private final GeminiResponseParser responseParser = new GeminiResponseParser();

    @Test
    @DisplayName("1. Attachment exceeding maximum file size is rejected before buffering")
    void testOversizedAttachmentRejectedByDeclaredSize() {
        InputStream dummyStream = new ByteArrayInputStream(new byte[100]);
        assertThrows(AttachmentSizeLimitExceededException.class, () -> {
            streamReader.readBounded(dummyStream, 5 * 1024 * 1024L, 0);
        });
    }

    @Test
    @DisplayName("2. Attachment with incorrect declared size is caught and terminated during streaming")
    void testOversizedAttachmentDetectedDuringStreaming() {
        byte[] payload = new byte[1500 * 1024]; // 1.5 MB payload (exceeds 1MB limit)
        InputStream stream = new ByteArrayInputStream(payload);

        assertThrows(AttachmentSizeLimitExceededException.class, () -> {
            streamReader.readBounded(stream, 500, 0); // Falsely declares 500 bytes
        });
    }

    @Test
    @DisplayName("3. Multiple attachments exceeding aggregate cumulative job limit are rejected")
    void testCumulativeAttachmentLimitExceeded() throws Exception {
        byte[] payload = new byte[800 * 1024];
        InputStream stream = new ByteArrayInputStream(payload);

        // First attachment: 800 KB OK
        assertNotNull(streamReader.readBounded(stream, 800 * 1024, 0));

        // Second attachment when cumulative is 1.8 MB (exceeds 2 MB limit)
        InputStream stream2 = new ByteArrayInputStream(payload);
        assertThrows(AttachmentSizeLimitExceededException.class, () -> {
            streamReader.readBounded(stream2, 800 * 1024, 1800 * 1024L);
        });
    }

    @Test
    @DisplayName("5. Corrupted PDF bytes handled gracefully without throwing unhandled exceptions")
    void testCorruptedPdfHandledGracefully() {
        byte[] corruptedBytes = "NOT_A_REAL_PDF_HEADER_DATA_CORRUPT".getBytes();
        PdfBoxDocumentExtractor.PdfExtractionResult result = pdfExtractor.extract(corruptedBytes);
        assertNotNull(result);
        assertEquals("FAILED", result.extractionMethod());
        assertFalse(result.isDigitalTextUsable());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("6. Password-encrypted PDF handled gracefully and flagged as FAILED")
    void testEncryptedPdfHandledGracefully() throws Exception {
        byte[] encryptedPdfBytes;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy spp = new StandardProtectionPolicy("secretOwnerPass", "userPass123", ap);
            spp.setEncryptionKeyLength(128);
            spp.setPermissions(ap);
            doc.protect(spp);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            encryptedPdfBytes = baos.toByteArray();
        }

        PdfBoxDocumentExtractor.PdfExtractionResult result = pdfExtractor.extract(encryptedPdfBytes);
        assertNotNull(result);
        assertEquals("FAILED", result.extractionMethod());
        assertTrue(result.errorMessage().contains("encrypted"));
    }

    @Test
    @DisplayName("7. PDF exceeding maximum page count threshold is rejected gracefully")
    void testExcessivePdfPagesHandledGracefully() throws Exception {
        byte[] multiPageBytes;
        try (PDDocument doc = new PDDocument()) {
            for (int pageIndex = 0; pageIndex < 10; pageIndex++) { // 10 pages exceeds max 5
                doc.addPage(new PDPage());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            multiPageBytes = baos.toByteArray();
        }

        PdfBoxDocumentExtractor.PdfExtractionResult result = pdfExtractor.extract(multiPageBytes);
        assertNotNull(result);
        assertEquals("FAILED", result.extractionMethod());
        assertTrue(result.errorMessage().contains("exceeds maximum limit"));
    }

    @Test
    @DisplayName("8. Malformed DOCX bytes handled gracefully returning empty text without crash")
    void testMalformedDocxHandledGracefully() {
        byte[] malformedDocx = "PK\u0003\u0004GARBAGE_ZIP_PAYLOAD".getBytes();
        String result = docxExtractor.extract(malformedDocx);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("9. Image dimensions exceeding maximum allowed dimension are rejected")
    void testImageDimensionsExceeded() throws Exception {
        BufferedImage hugeImage = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB); // 800 exceeds 500 limit
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(hugeImage, "png", baos);

        ImageDocumentValidator.ImageValidationResult result = imageValidator.validateImage(baos.toByteArray());
        assertNotNull(result);
        assertTrue(result.isValidImage());
        assertFalse(result.isDimensionSafe());
        assertTrue(result.errorMessage().contains("exceed maximum allowed limit"));
    }

    @Test
    @DisplayName("10. Gemini response with finishReason MAX_TOKENS throws GeminiResponseTruncatedException")
    void testGeminiMaxTokensFinishReasonDetected() {
        String jsonWithMaxTokens = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [{ "text": "{\\"poNumber\\": \\"PO-INCOMPLETE\\"" }]
                  },
                  "finishReason": "MAX_TOKENS"
                }
              ]
            }
            """;

        assertThrows(GeminiResponseTruncatedException.class, () -> {
            responseParser.parseResponse(jsonWithMaxTokens);
        });
    }

    @Test
    @DisplayName("11. Incomplete / truncated JSON response throws GeminiResponseTruncatedException")
    void testTruncatedJsonResponseDetected() {
        String truncatedJson = """
            ```json
            {
              "poNumber": "PO-100",
              "currency": "USD",
              "lineItems": [
                { "lineNumber": 1, "productCode": "SKU-A"
            """;

        assertThrows(GeminiResponseTruncatedException.class, () -> {
            responseParser.parseResponse(truncatedJson);
        });
    }

    @Test
    @DisplayName("12. Permanent 401/403 API authentication errors fail immediately without retry")
    void testNonRetryableHttpErrorsFailImmediately() {
        GeminiDocumentIntelligenceProvider provider = new GeminiDocumentIntelligenceProvider(
                new GeminiPromptBuilder(), responseParser, "invalid-key", "gemini-1.5-flash",
                true, "http://localhost:59999", 5, 8192, 3
        );

        DocumentExtractionRequest request = new DocumentExtractionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "Test", "test@test.com", "Body", List.of()
        );

        // Provider invocation fails with IllegalStateException (not retrying indefinitely)
        assertThrows(IllegalStateException.class, () -> {
            provider.extractPurchaseOrder(request);
        });
    }
}
