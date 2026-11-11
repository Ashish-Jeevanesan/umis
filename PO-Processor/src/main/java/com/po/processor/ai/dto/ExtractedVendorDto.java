package com.po.processor.ai.dto;

/**
 * Data transfer object representing extracted vendor / supplier company details.
 */
public record ExtractedVendorDto(
        String vendorName,
        String vendorAddress,
        String vendorContact,
        String vendorEmail,
        String vendorPhone,
        String vendorTaxId,
        String vendorAccountNumber
) {}
