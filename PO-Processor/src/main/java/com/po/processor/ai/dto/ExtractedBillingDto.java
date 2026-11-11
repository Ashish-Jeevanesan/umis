package com.po.processor.ai.dto;

/**
 * Data transfer object representing extracted billing and invoicing details.
 */
public record ExtractedBillingDto(
        String billingEntity,
        String billingContact,
        String billingAddress,
        String city,
        String stateRegion,
        String postalCode,
        String country,
        String taxId
) {}
