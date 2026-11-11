package com.po.processor.ai.dto;

import java.time.LocalDate;

/**
 * Data transfer object representing extracted shipping and delivery destination details.
 */
public record ExtractedShippingDto(
        String shipToEntity,
        String hospitalLocationName,
        String shippingContact,
        String shippingAddress,
        String city,
        String stateRegion,
        String postalCode,
        String country,
        String deliveryInstructions,
        LocalDate requestedDeliveryDate
) {}
