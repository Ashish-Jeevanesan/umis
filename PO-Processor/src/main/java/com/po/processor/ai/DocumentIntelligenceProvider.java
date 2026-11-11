package com.po.processor.ai;

import com.po.processor.ai.dto.DocumentExtractionRequest;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;

/**
 * Pluggable abstraction for AI document understanding and structured purchase-order extraction.
 */
public interface DocumentIntelligenceProvider {

    /**
     * Extracts structured purchase order details from email body and attachment inputs.
     *
     * @param request DocumentExtractionRequest containing email text and attachments
     * @return ExtractedPurchaseOrderDto with structured fields and evidence
     */
    ExtractedPurchaseOrderDto extractPurchaseOrder(DocumentExtractionRequest request);

    /**
     * Returns the provider identifier (e.g., "GEMINI").
     *
     * @return Provider identifier string
     */
    String getProviderName();

    /**
     * Returns the active model name (e.g., "gemini-1.5-flash").
     *
     * @return Model name string
     */
    String getModelName();
}
