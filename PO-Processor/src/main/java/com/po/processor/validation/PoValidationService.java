package com.po.processor.validation;

import com.po.processor.entity.ExtractedPoLineItem;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.PoValidationResult;
import com.po.processor.repository.ExtractedPoLineItemRepository;
import com.po.processor.repository.PoValidationResultRepository;
import com.po.processor.validation.dto.ValidationSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates financial equations, tax consistency, and mandatory business rules on extracted POs.
 */
@Service
public class PoValidationService {

    private static final Logger log = LoggerFactory.getLogger(PoValidationService.class);
    private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.02");

    private final PoValidationResultRepository validationResultRepository;
    private final ExtractedPoLineItemRepository lineItemRepository;

    public PoValidationService(
            PoValidationResultRepository validationResultRepository,
            ExtractedPoLineItemRepository lineItemRepository
    ) {
        this.validationResultRepository = validationResultRepository;
        this.lineItemRepository = lineItemRepository;
    }

    /**
     * Executes all validation rules against an extracted purchase order and records the results.
     *
     * @param po ExtractedPurchaseOrder entity
     * @return ValidationSummaryDto summarizing rule outcomes
     */
    @Transactional
    public ValidationSummaryDto validatePurchaseOrder(ExtractedPurchaseOrder po) {
        List<PoValidationResult> alResults = new ArrayList<>();
        List<ExtractedPoLineItem> alItems = lineItemRepository.findByPurchaseOrderIdOrderByLineNumberAsc(po.getId());

        // 1. Mandatory Business Fields
        validateMandatoryField(po, "REQ_PO_NUMBER", "Purchase Order Number", po.getPoNumber(), alResults);
        validateMandatoryField(po, "REQ_CURRENCY", "Currency", po.getCurrency(), alResults);
        validateMandatoryField(po, "REQ_VENDOR_NAME", "Vendor Name", po.getVendorName(), alResults);
        validateMandatoryField(po, "REQ_HOSPITAL", "Hospital Resolution",
                po.getHospital() != null ? po.getHospital().getNormalizedName() : null, alResults);

        // 2. Financial Validations
        validatePositiveTotal(po, alResults);
        validateLineItemsSum(po, alItems, alResults);
        validateBalanceEquation(po, alResults);
        validateLineItemArithmetic(po, alItems, alResults);

        // Delete old validation records and persist new findings
        validationResultRepository.deleteByPurchaseOrderId(po.getId());
        validationResultRepository.saveAll(alResults);

        int errorCount = (int) alResults.stream().filter(res -> !res.isPassed() && "ERROR".equals(res.getSeverity())).count();
        int warningCount = (int) alResults.stream().filter(res -> !res.isPassed() && "WARNING".equals(res.getSeverity())).count();
        boolean isValid = errorCount == 0;

        log.info("Validation completed for PO [{}]: valid={}, errors={}, warnings={}",
                po.getPoNumber(), isValid, errorCount, warningCount);

        return new ValidationSummaryDto(isValid, errorCount, warningCount, alResults);
    }

    private void validateMandatoryField(
            ExtractedPurchaseOrder po,
            String strRuleCode,
            String strRuleName,
            String strValue,
            List<PoValidationResult> alResults
    ) {
        boolean passed = strValue != null && !strValue.isBlank();
        String strExplanation = passed ? strRuleName + " is provided." : strRuleName + " is missing or blank.";
        alResults.add(new PoValidationResult(
                po, strRuleCode, strRuleName, "ERROR", passed, strExplanation
        ));
    }

    private void validatePositiveTotal(ExtractedPurchaseOrder po, List<PoValidationResult> alResults) {
        boolean passed = po.getGrandTotal() != null && po.getGrandTotal().compareTo(BigDecimal.ZERO) > 0;
        String strExplanation = passed
                ? "Grand total is positive (" + po.getGrandTotal() + ")."
                : "Grand total must be greater than zero.";
        alResults.add(new PoValidationResult(
                po, "FIN_POSITIVE_TOTAL", "Positive Grand Total", "ERROR", passed, strExplanation
        ));
    }

    private void validateLineItemsSum(
            ExtractedPurchaseOrder po,
            List<ExtractedPoLineItem> alItems,
            List<PoValidationResult> alResults
    ) {
        if (alItems.isEmpty()) {
            alResults.add(new PoValidationResult(
                    po, "FIN_LINE_ITEMS_PRESENT", "Line Items Present", "WARNING", false,
                    "No individual line items were extracted."
            ));
            return;
        }

        BigDecimal bdSum = BigDecimal.ZERO;
        for (ExtractedPoLineItem item : alItems) {
            if (item.getLineTotal() != null) {
                bdSum = bdSum.add(item.getLineTotal());
            }
        }

        BigDecimal target = po.getSubtotal() != null && po.getSubtotal().compareTo(BigDecimal.ZERO) > 0
                ? po.getSubtotal() : po.getGrandTotal();

        boolean passed = target != null && bdSum.subtract(target).abs().compareTo(ROUNDING_TOLERANCE) <= 0;
        String strExplanation = passed
                ? "Sum of line items matches expected target (" + bdSum + ")."
                : "Sum of line items (" + bdSum + ") differs from subtotal/total (" + target + ").";

        alResults.add(new PoValidationResult(
                po, "FIN_LINE_ITEMS_SUM", "Line Items Sum Reconciliation", "WARNING", passed, strExplanation
        ));
    }

    private void validateBalanceEquation(ExtractedPurchaseOrder po, List<PoValidationResult> alResults) {
        if (po.getSubtotal() == null || po.getGrandTotal() == null) {
            return;
        }

        BigDecimal discount = po.getDiscount() != null ? po.getDiscount() : BigDecimal.ZERO;
        BigDecimal freight = po.getFreightCharges() != null ? po.getFreightCharges() : BigDecimal.ZERO;
        BigDecimal tax = po.getTaxAmount() != null ? po.getTaxAmount() : BigDecimal.ZERO;

        BigDecimal calculatedTotal = po.getSubtotal().subtract(discount).add(freight).add(tax);
        boolean passed = calculatedTotal.subtract(po.getGrandTotal()).abs().compareTo(ROUNDING_TOLERANCE) <= 0;

        String strExplanation = passed
                ? "Financial balance equation verified: Subtotal - Discount + Freight + Tax = Grand Total."
                : "Balance discrepancy: Subtotal (" + po.getSubtotal() + ") - Discount (" + discount + ") + Freight (" + freight
                + ") + Tax (" + tax + ") = " + calculatedTotal + " vs Grand Total (" + po.getGrandTotal() + ").";

        alResults.add(new PoValidationResult(
                po, "FIN_BALANCE_EQUATION", "Financial Balance Equation", "WARNING", passed, strExplanation
        ));
    }

    private void validateLineItemArithmetic(
            ExtractedPurchaseOrder po,
            List<ExtractedPoLineItem> alItems,
            List<PoValidationResult> alResults
    ) {
        if (alItems == null || alItems.isEmpty()) {
            return;
        }

        boolean hasDiscrepancy = false;
        for (ExtractedPoLineItem item : alItems) {
            if (item.getQuantity() != null && item.getUnitPrice() != null && item.getLineTotal() != null) {
                BigDecimal expectedLineTotal = item.getQuantity().multiply(item.getUnitPrice());
                BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
                BigDecimal expectedWithDiscount = expectedLineTotal.subtract(discount);

                boolean exactMatch = expectedLineTotal.subtract(item.getLineTotal()).abs().compareTo(ROUNDING_TOLERANCE) <= 0
                        || expectedWithDiscount.subtract(item.getLineTotal()).abs().compareTo(ROUNDING_TOLERANCE) <= 0;

                if (!exactMatch) {
                    hasDiscrepancy = true;
                    String strMsg = String.format(
                            "Line %d (%s) discrepancy: Qty (%s) x Unit Price (%s) = %s, but line total is %s.",
                            item.getLineNumber(), item.getProductCode() != null ? item.getProductCode() : "N/A",
                            item.getQuantity(), item.getUnitPrice(), expectedLineTotal, item.getLineTotal()
                    );
                    alResults.add(new PoValidationResult(
                            po, "FIN_LINE_ARITHMETIC", "Line Item Arithmetic Discrepancy", "WARNING", false, strMsg
                    ));
                }
            }
        }

        if (!hasDiscrepancy) {
            alResults.add(new PoValidationResult(
                    po, "FIN_LINE_ARITHMETIC", "Line Item Arithmetic Discrepancy", "WARNING", true,
                    "All line items satisfy Quantity x Unit Price = Line Total."
            ));
        }
    }
}
