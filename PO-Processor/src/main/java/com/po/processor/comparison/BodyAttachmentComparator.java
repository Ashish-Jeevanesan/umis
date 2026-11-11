package com.po.processor.comparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.comparison.dto.ComparisonResultDto;
import com.po.processor.comparison.dto.FieldComparisonItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares email body text against attachment-extracted PO details to identify matches and conflicts.
 */
@Service
public class BodyAttachmentComparator {

    private static final Logger log = LoggerFactory.getLogger(BodyAttachmentComparator.class);
    private static final Pattern PO_NUMBER_PATTERN = Pattern.compile("(?i)(?:po|order|purchase\\s*order)[\\s#:.-]*([A-Z0-9_-]{4,30})");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:[$€£₹]|EUR|USD|INR)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes side-by-side reconciliation between email body text and the attachment extraction.
     *
     * @param strEmailBody Clean email body text
     * @param poDto Extracted PO DTO from attachment
     * @param hasAttachments Whether attachments were present
     * @return ComparisonResultDto containing status, conflict count, and detailed field report
     */
    public ComparisonResultDto compare(String strEmailBody, ExtractedPurchaseOrderDto poDto, boolean hasAttachments) {
        List<FieldComparisonItemDto> alComparisons = new ArrayList<>();
        int conflictCount = 0;

        String strEmailClean = strEmailBody != null ? strEmailBody.trim() : "";
        boolean isEmailEmpty = strEmailClean.isBlank();
        boolean hasEmailPoMention = hasPoKeywords(strEmailClean);

        if (!hasAttachments && poDto == null) {
            return new ComparisonResultDto("NO_PURCHASE_ORDER_FOUND", 0, List.of(), "[]");
        }

        if (!hasAttachments && poDto != null) {
            return new ComparisonResultDto("BODY_ONLY_RELEVANT", 0, List.of(), "[]");
        }

        if (poDto == null) {
            return new ComparisonResultDto("NO_PURCHASE_ORDER_FOUND", 0, List.of(), "[]");
        }

        // 1. PO Number Comparison
        String strEmailPo = extractRegexMatch(PO_NUMBER_PATTERN, strEmailClean);
        String strAttPo = poDto.poNumber();
        conflictCount += evaluateField("PO Number", strEmailPo, strAttPo, alComparisons);

        // 2. Amount Comparison
        String strEmailAmount = extractRegexMatch(AMOUNT_PATTERN, strEmailClean);
        String strAttAmount = poDto.grandTotal() != null ? poDto.grandTotal().toPlainString() : null;
        conflictCount += evaluateAmount("Total Amount", strEmailAmount, strAttAmount, alComparisons);

        // 3. Currency Comparison
        String strEmailCurrency = detectCurrency(strEmailClean);
        String strAttCurrency = poDto.currency();
        conflictCount += evaluateField("Currency", strEmailCurrency, strAttCurrency, alComparisons);

        // 4. Hospital Comparison
        String strAttHospital = poDto.hospitalName();
        conflictCount += evaluateSubstr("Hospital", strEmailClean, strAttHospital, alComparisons);

        // 5. Vendor Comparison
        String strAttVendor = poDto.vendor() != null ? poDto.vendor().vendorName() : null;
        conflictCount += evaluateSubstr("Vendor", strEmailClean, strAttVendor, alComparisons);

        // Determine overall comparison status
        String strStatus = determineStatus(isEmailEmpty, hasEmailPoMention, conflictCount, alComparisons);

        String strReportJson = serializeReport(alComparisons);
        return new ComparisonResultDto(strStatus, conflictCount, alComparisons, strReportJson);
    }

    private int evaluateField(String strField, String strEmailVal, String strAttVal, List<FieldComparisonItemDto> alReport) {
        if (strEmailVal == null && strAttVal == null) return 0;
        if (strEmailVal == null) {
            alReport.add(new FieldComparisonItemDto(strField, "-", strAttVal, "MISSING_IN_EMAIL", "Present only in attachment"));
            return 0;
        }
        if (strAttVal == null) {
            alReport.add(new FieldComparisonItemDto(strField, strEmailVal, "-", "MISSING_IN_ATTACHMENT", "Present only in email"));
            return 0;
        }

        boolean isMatch = strEmailVal.equalsIgnoreCase(strAttVal);
        String strResult = isMatch ? "MATCH" : "MISMATCH";
        String strComment = isMatch ? "Values match" : "Conflict: Email has [" + strEmailVal + "] vs Attachment [" + strAttVal + "]";
        alReport.add(new FieldComparisonItemDto(strField, strEmailVal, strAttVal, strResult, strComment));
        return isMatch ? 0 : 1;
    }

    private int evaluateAmount(String strField, String strEmailVal, String strAttVal, List<FieldComparisonItemDto> alReport) {
        if (strEmailVal == null && strAttVal == null) return 0;
        if (strEmailVal == null) {
            alReport.add(new FieldComparisonItemDto(strField, "-", strAttVal, "MISSING_IN_EMAIL", "Amount not stated in email"));
            return 0;
        }
        if (strAttVal == null) {
            alReport.add(new FieldComparisonItemDto(strField, strEmailVal, "-", "MISSING_IN_ATTACHMENT", "Amount missing in attachment"));
            return 0;
        }

        try {
            BigDecimal bdEmail = new BigDecimal(strEmailVal.replace(",", ""));
            BigDecimal bdAtt = new BigDecimal(strAttVal.replace(",", ""));
            boolean isMatch = bdEmail.compareTo(bdAtt) == 0;
            String strResult = isMatch ? "MATCH" : "MISMATCH";
            String strComment = isMatch ? "Amounts match" : "Conflict: Email [" + bdEmail + "] vs Attachment [" + bdAtt + "]";
            alReport.add(new FieldComparisonItemDto(strField, strEmailVal, strAttVal, strResult, strComment));
            return isMatch ? 0 : 1;
        } catch (Exception ex) {
            return evaluateField(strField, strEmailVal, strAttVal, alReport);
        }
    }

    private int evaluateSubstr(String strField, String strEmailBody, String strAttVal, List<FieldComparisonItemDto> alReport) {
        if (strAttVal == null || strAttVal.isBlank()) return 0;
        boolean contains = strEmailBody.toLowerCase(Locale.ROOT).contains(strAttVal.toLowerCase(Locale.ROOT));
        String strResult = contains ? "MATCH" : "MISSING_IN_EMAIL";
        String strComment = contains ? "Found in email body" : "Not explicitly referenced in email body";
        alReport.add(new FieldComparisonItemDto(strField, contains ? strAttVal : "-", strAttVal, strResult, strComment));
        return 0;
    }

    private String determineStatus(boolean isEmailEmpty, boolean hasPoMention, int conflicts, List<FieldComparisonItemDto> alReport) {
        if (conflicts > 0) {
            return "BODY_ATTACHMENT_MISMATCH";
        }
        if (isEmailEmpty || !hasPoMention) {
            return "ATTACHMENT_ONLY_RELEVANT";
        }
        long matchCount = alReport.stream().filter(item -> "MATCH".equals(item.matchResult())).count();
        if (matchCount >= 2) {
            return "BODY_AND_ATTACHMENT_MATCH";
        }
        return "ATTACHMENT_ONLY_RELEVANT";
    }

    private boolean hasPoKeywords(String strText) {
        String strLower = strText.toLowerCase(Locale.ROOT);
        return strLower.contains("purchase order") || strLower.contains("po #") || strLower.contains("po#")
                || strLower.contains("order total") || strLower.contains("attached po");
    }

    private String extractRegexMatch(Pattern pattern, String strText) {
        Matcher matcher = pattern.matcher(strText);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String detectCurrency(String strText) {
        if (strText.contains("EUR") || strText.contains("€")) return "EUR";
        if (strText.contains("USD") || strText.contains("$")) return "USD";
        if (strText.contains("INR") || strText.contains("₹")) return "INR";
        if (strText.contains("GBP") || strText.contains("£")) return "GBP";
        return null;
    }

    private String serializeReport(List<FieldComparisonItemDto> alReport) {
        try {
            return objectMapper.writeValueAsString(alReport);
        } catch (Exception exception) {
            log.error("Failed serializing comparison report to JSON", exception);
            return "[]";
        }
    }
}
