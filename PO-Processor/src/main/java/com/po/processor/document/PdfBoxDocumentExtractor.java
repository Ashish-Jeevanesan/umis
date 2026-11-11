package com.po.processor.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Service extracting text and raster images from PDF documents using Apache PDFBox with memory and page limits.
 */
@Service
public class PdfBoxDocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxDocumentExtractor.class);
    private static final int MIN_CHARS_PER_PAGE_THRESHOLD = 50;

    private final int maxPdfPages;

    public PdfBoxDocumentExtractor() {
        this(50);
    }

    public PdfBoxDocumentExtractor(@Value("${po.attachment.max-pdf-pages:50}") int maxPdfPages) {
        this.maxPdfPages = maxPdfPages;
    }

    /**
     * Extraction result holder for PDF documents.
     */
    public record PdfExtractionResult(
            String textContent,
            int pageCount,
            boolean isDigitalTextUsable,
            String extractionMethod,
            String base64ImageSample,
            String errorMessage
    ) {
        public PdfExtractionResult(
                String textContent,
                int pageCount,
                boolean isDigitalTextUsable,
                String extractionMethod,
                String base64ImageSample
        ) {
            this(textContent, pageCount, isDigitalTextUsable, extractionMethod, base64ImageSample, null);
        }
    }

    /**
     * Extracts text from PDF bytes, enforcing page count and encryption safeguards.
     *
     * @param bytePdf PDF binary content
     * @return PdfExtractionResult with text, page count, and classification
     */
    public PdfExtractionResult extract(byte[] bytePdf) {
        if (bytePdf == null || bytePdf.length == 0) {
            return new PdfExtractionResult("", 0, false, "FAILED", null, "Empty PDF file content");
        }

        try (PDDocument document = Loader.loadPDF(bytePdf)) {
            if (document.isEncrypted()) {
                log.warn("Cannot parse password-encrypted PDF attachment");
                return new PdfExtractionResult("", 0, false, "FAILED", null, "PDF is password encrypted");
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount > maxPdfPages) {
                log.warn("PDF page count ({}) exceeds threshold ({})", pageCount, maxPdfPages);
                return new PdfExtractionResult(
                        "", pageCount, false, "FAILED", null,
                        "PDF page count (" + pageCount + ") exceeds maximum limit (" + maxPdfPages + ")"
                );
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String strText = stripper.getText(document).trim();

            int avgCharsPerPage = pageCount > 0 ? strText.length() / pageCount : 0;
            boolean isUsable = avgCharsPerPage >= MIN_CHARS_PER_PAGE_THRESHOLD;

            String strMethod = isUsable ? "DIGITAL_TEXT_EXTRACTION" : "IMAGE_VISION_EXTRACTION";
            String strBase64Image = null;

            if (!isUsable) {
                strBase64Image = renderFirstPageToBase64(document);
            }

            return new PdfExtractionResult(strText, pageCount, isUsable, strMethod, strBase64Image, null);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException passEx) {
            log.warn("Cannot parse password-encrypted PDF: {}", passEx.getMessage());
            return new PdfExtractionResult("", 0, false, "FAILED", null, "PDF is password encrypted: " + passEx.getMessage());
        } catch (Exception exception) {
            log.error("Failed to parse PDF document with PDFBox", exception);
            return new PdfExtractionResult("", 0, false, "FAILED", null, exception.getMessage());
        }
    }

    private String renderFirstPageToBase64(PDDocument document) {
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 120, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception exception) {
            log.warn("Failed rendering first PDF page to image for vision fallback", exception);
            return null;
        }
    }
}
