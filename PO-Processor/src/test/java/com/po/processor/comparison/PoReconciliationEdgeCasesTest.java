package com.po.processor.comparison;

import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.ai.dto.ExtractedVendorDto;
import com.po.processor.comparison.dto.ComparisonResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge-case test suite for side-by-side reconciliation between email bodies and attachment extractions.
 */
public class PoReconciliationEdgeCasesTest {

    private final BodyAttachmentComparator comparator = new BodyAttachmentComparator();

    private ExtractedPurchaseOrderDto createSampleDto(String poNumber, String currency, BigDecimal grandTotal) {
        return new ExtractedPurchaseOrderDto(
                poNumber, "Surgical Order", LocalDate.now(), null, null,
                currency, grandTotal, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, grandTotal,
                "Net 30", "Wire", null, null, null, null,
                "Mercy Hospital", "MERCY-01",
                new ExtractedVendorDto("Globus Medical", null, null, null, null, null, null),
                null, null, null, List.of(), List.of()
        );
    }

    @Test
    @DisplayName("1. Conflicting PO numbers between email body and attachment produces MISMATCH and conflict")
    void testConflictingPoNumberProducesMismatch() {
        ExtractedPurchaseOrderDto dto = createSampleDto("PO-ATT-999", "USD", new BigDecimal("5000.00"));
        String strEmailBody = "Please find attached Purchase Order PO-BODY-111 for processing.";

        ComparisonResultDto result = comparator.compare(strEmailBody, dto, true);
        assertEquals("BODY_ATTACHMENT_MISMATCH", result.comparisonStatus());
        assertTrue(result.conflictCount() >= 1);
    }

    @Test
    @DisplayName("2. Conflicting currencies between email and attachment produces conflict")
    void testConflictingCurrenciesProducesConflict() {
        ExtractedPurchaseOrderDto dto = createSampleDto("PO-CURR-01", "USD", new BigDecimal("5000.00"));
        String strEmailBody = "PO-CURR-01 approved for €5,000.00 EUR.";

        ComparisonResultDto result = comparator.compare(strEmailBody, dto, true);
        assertEquals("BODY_ATTACHMENT_MISMATCH", result.comparisonStatus());
        assertTrue(result.conflictCount() >= 1);
    }

    @Test
    @DisplayName("3. Conflicting grand totals produces mismatch with conflictCount")
    void testConflictingTotalsProducesConflict() {
        ExtractedPurchaseOrderDto dto = createSampleDto("PO-TOT-01", "USD", new BigDecimal("5000.00"));
        String strEmailBody = "Order PO-TOT-01 confirmed at $8,500.00 USD total.";

        ComparisonResultDto result = comparator.compare(strEmailBody, dto, true);
        assertEquals("BODY_ATTACHMENT_MISMATCH", result.comparisonStatus());
        assertTrue(result.conflictCount() >= 1);
    }

    @Test
    @DisplayName("4. Email-only PO without attachments produces BODY_ONLY_RELEVANT")
    void testEmailOnlyPoProducesBodyOnlyRelevant() {
        ExtractedPurchaseOrderDto dto = createSampleDto("PO-EMAIL-01", "USD", new BigDecimal("1200.00"));
        String strEmailBody = "Please process purchase order PO-EMAIL-01 for $1,200.00.";

        ComparisonResultDto result = comparator.compare(strEmailBody, dto, false);
        assertEquals("BODY_ONLY_RELEVANT", result.comparisonStatus());
        assertEquals(0, result.conflictCount());
    }

    @Test
    @DisplayName("5. Attachment-only PO with generic cover email produces ATTACHMENT_ONLY_RELEVANT")
    void testAttachmentOnlyProducesAttachmentOnlyRelevant() {
        ExtractedPurchaseOrderDto dto = createSampleDto("PO-ATT-ONLY", "USD", new BigDecimal("3400.00"));
        String strEmailBody = "Please see the attached file for your records. Thanks!";

        ComparisonResultDto result = comparator.compare(strEmailBody, dto, true);
        assertEquals("ATTACHMENT_ONLY_RELEVANT", result.comparisonStatus());
        assertEquals(0, result.conflictCount());
    }

    @Test
    @DisplayName("6. Fully matching body and attachment produces BODY_AND_ATTACHMENT_MATCH")
    void testFullyMatchingEmailAndAttachment() {
        ExtractedPurchaseOrderDto dto = createSampleDto("PO-MATCH-01", "USD", new BigDecimal("2500.00"));
        String strEmailBody = "Here is Purchase Order PO-MATCH-01 for $2,500.00 USD for Mercy Hospital.";

        ComparisonResultDto result = comparator.compare(strEmailBody, dto, true);
        assertEquals("BODY_AND_ATTACHMENT_MATCH", result.comparisonStatus());
        assertEquals(0, result.conflictCount());
    }
}
