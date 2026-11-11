package com.po.processor.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Structured purchase-order extraction outcome returned by DocumentIntelligenceProvider.
 */
public record ExtractedPurchaseOrderDto(
        String poNumber,
        String poTitleDescription,
        LocalDate poDate,
        LocalDate requestedDeliveryDate,
        LocalDate expectedDeliveryDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal freightCharges,
        BigDecimal taxAmount,
        BigDecimal taxPercentage,
        BigDecimal grandTotal,
        String paymentTerms,
        String paymentMethod,
        String deliveryInstructions,
        String incoterms,
        String contractReferenceNumber,
        String quotationReference,
        String hospitalName,
        String hospitalCode,
        ExtractedVendorDto vendor,
        ExtractedPatientDto patient,
        ExtractedBillingDto billing,
        ExtractedShippingDto shipping,
        List<ExtractedLineItemDto> lineItems,
        List<FieldEvidenceDto> fieldEvidence
) {}
