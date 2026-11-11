package com.po.processor.document;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Service for document classification and SHA-256 fingerprinting.
 */
@Service
public class DocumentClassificationService {

    /**
     * Computes the SHA-256 hash of document bytes.
     *
     * @param byteContent Binary file content
     * @return Lowercase hex SHA-256 string
     */
    public String calculateSha256(byte[] byteContent) {
        if (byteContent == null || byteContent.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteHash = digest.digest(byteContent);
            return HexFormat.of().formatHex(byteHash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }
    }

    /**
     * Classifies a document based on its filename, MIME type, and extracted text content.
     *
     * @param strFileName Name of the file
     * @param strMimeType MIME type
     * @param strText Extracted text content
     * @return Classification category
     */
    public String classifyDocument(String strFileName, String strMimeType, String strText) {
        String strLowerName = strFileName != null ? strFileName.toLowerCase(Locale.ROOT) : "";
        String strLowerText = strText != null ? strText.toLowerCase(Locale.ROOT) : "";

        if (strLowerName.contains("purchase_order") || strLowerName.contains("purchase order")
                || strLowerName.contains("po_") || strLowerName.startsWith("po-")
                || strLowerText.contains("purchase order") || strLowerText.contains("order confirmation")) {
            return "PURCHASE_ORDER";
        }

        if (strLowerName.contains("invoice") || strLowerName.startsWith("inv-")
                || strLowerText.contains("tax invoice") || strLowerText.contains("commercial invoice")) {
            return "INVOICE";
        }

        if (strLowerName.contains("quote") || strLowerName.contains("quotation")
                || strLowerText.contains("price quotation") || strLowerText.contains("estimate")) {
            return "QUOTATION";
        }

        if (strLowerName.contains("delivery") || strLowerName.contains("packing_slip")
                || strLowerText.contains("delivery note") || strLowerText.contains("bill of lading")) {
            return "DELIVERY_NOTE";
        }

        if (strLowerName.contains("spec") || strLowerName.contains("compliance") || strLowerName.contains("cert")) {
            return "SUPPORTING_DOCUMENT";
        }

        if (strMimeType != null && (strMimeType.contains("pdf") || strMimeType.contains("word")
                || strMimeType.contains("image"))) {
            return "UNKNOWN";
        }

        return "UNRELATED";
    }
}
