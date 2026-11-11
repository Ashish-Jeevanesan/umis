package com.po.processor.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying PDFBox, Apache POI DOCX, and Document Classification capabilities.
 */
public class DocumentExtractorTest {

    private final PdfBoxDocumentExtractor pdfExtractor = new PdfBoxDocumentExtractor();
    private final DocxDocumentExtractor docxExtractor = new DocxDocumentExtractor();
    private final DocumentClassificationService classificationService = new DocumentClassificationService();

    @Test
    @DisplayName("Verify PDFBox extracts digital text from synthetic PDF document")
    void testPdfDigitalTextExtraction() throws Exception {
        byte[] bytePdf;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Purchase Order Number: PO-2026-TEST-999");
                stream.newLineAtOffset(0, -20);
                stream.showText("Hospital: St. Jude Children's Hospital. Total Amount: $45,000.00 USD.");
                stream.newLineAtOffset(0, -20);
                stream.showText("Item: Sterile Orthopedic Implants, Qty: 10, Unit: $4,500.00.");
                stream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            bytePdf = baos.toByteArray();
        }

        PdfBoxDocumentExtractor.PdfExtractionResult result = pdfExtractor.extract(bytePdf);
        assertNotNull(result);
        assertEquals(1, result.pageCount());
        assertTrue(result.isDigitalTextUsable());
        assertEquals("DIGITAL_TEXT_EXTRACTION", result.extractionMethod());
        assertTrue(result.textContent().contains("PO-2026-TEST-999"));
        assertTrue(result.textContent().contains("St. Jude"));
    }

    @Test
    @DisplayName("Verify DocxDocumentExtractor extracts paragraph and table text from DOCX")
    void testDocxExtraction() throws Exception {
        byte[] byteDocx;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("Commercial Purchase Order PO-DOCX-101");

            XWPFTable table = doc.createTable();
            XWPFTableRow header = table.getRow(0);
            header.getCell(0).setText("Item");
            header.addNewTableCell().setText("Price");

            XWPFTableRow row = table.createRow();
            row.getCell(0).setText("Catheter Pack");
            row.getCell(1).setText("$1,200.00");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            byteDocx = baos.toByteArray();
        }

        String strExtracted = docxExtractor.extract(byteDocx);
        assertNotNull(strExtracted);
        assertTrue(strExtracted.contains("PO-DOCX-101"));
        assertTrue(strExtracted.contains("[TABLE START]"));
        assertTrue(strExtracted.contains("Catheter Pack"));
        assertTrue(strExtracted.contains("$1,200.00"));
    }

    @Test
    @DisplayName("Verify classification and SHA-256 fingerprinting")
    void testClassificationAndHashing() {
        byte[] sampleData = "Synthetic Document Content for SHA256".getBytes();
        String strHash = classificationService.calculateSha256(sampleData);
        assertNotNull(strHash);
        assertEquals(64, strHash.length());

        assertEquals("PURCHASE_ORDER", classificationService.classifyDocument("PO_99812.pdf", "application/pdf", "Purchase Order"));
        assertEquals("INVOICE", classificationService.classifyDocument("Invoice_44.pdf", "application/pdf", "Tax Invoice Details"));
        assertEquals("QUOTATION", classificationService.classifyDocument("Quote_ABC.pdf", "application/pdf", "Price Quotation"));
        assertEquals("DELIVERY_NOTE", classificationService.classifyDocument("Delivery_Slip.pdf", "application/pdf", "Delivery Note"));
    }
}
