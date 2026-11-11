package com.po.processor.document;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.StringJoiner;

/**
 * Service extracting text and structured tables from DOCX attachments using Apache POI with ZIP-bomb safeguards.
 */
@Service
public class DocxDocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxDocumentExtractor.class);

    private final int maxDocxChars;

    public DocxDocumentExtractor() {
        this(500000);
    }

    public DocxDocumentExtractor(@Value("${po.attachment.max-docx-chars:500000}") int maxDocxChars) {
        this.maxDocxChars = maxDocxChars;
        // Configure strict ZIP bomb protection limits in Apache POI
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(25 * 1024 * 1024L);
    }

    /**
     * Extracts text content and table rows from DOCX document bytes.
     *
     * @param byteDocx DOCX binary content
     * @return Extracted text content with table representations
     */
    public String extract(byte[] byteDocx) {
        if (byteDocx == null || byteDocx.length == 0) {
            return "";
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(byteDocx);
             XWPFDocument document = new XWPFDocument(bais)) {

            StringBuilder sbResult = new StringBuilder();

            // 1. Paragraphs
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (sbResult.length() >= maxDocxChars) break;
                String strText = paragraph.getText();
                if (strText != null && !strText.isBlank()) {
                    sbResult.append(strText.trim()).append("\n");
                }
            }

            // 2. Tables
            for (XWPFTable table : document.getTables()) {
                if (sbResult.length() >= maxDocxChars) break;
                sbResult.append("\n[TABLE START]\n");
                for (XWPFTableRow row : table.getRows()) {
                    StringJoiner sjRow = new StringJoiner(" | ");
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sjRow.add(cell.getText().trim());
                    }
                    sbResult.append(sjRow).append("\n");
                }
                sbResult.append("[TABLE END]\n\n");
            }

            return sbResult.toString().trim();
        } catch (Exception exception) {
            log.warn("Failed extracting text from DOCX document (malformed or security limit): {}", exception.getMessage());
            return "";
        }
    }
}
