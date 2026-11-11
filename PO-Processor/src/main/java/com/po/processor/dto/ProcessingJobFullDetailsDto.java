package com.po.processor.dto;

import com.po.processor.entity.ExtractedPoLineItem;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.PoApprovalHistory;
import com.po.processor.entity.PoBillingDetail;
import com.po.processor.entity.PoBodyAttachmentComparison;
import com.po.processor.entity.PoDocumentAnalysis;
import com.po.processor.entity.PoDuplicateDetection;
import com.po.processor.entity.PoFieldEditHistory;
import com.po.processor.entity.PoPatientDetail;
import com.po.processor.entity.PoShippingDetail;
import com.po.processor.entity.PoValidationResult;

import java.util.List;

/**
 * Comprehensive DTO carrying all review sections, documents, comparisons, and audit history.
 */
public record ProcessingJobFullDetailsDto(
        ProcessingJobResponseDto job,
        ExtractedPurchaseOrder purchaseOrder,
        List<ExtractedPoLineItem> lineItems,
        PoPatientDetail patientDetail,
        PoBillingDetail billingDetail,
        PoShippingDetail shippingDetail,
        List<PoDocumentAnalysis> documentAnalyses,
        PoBodyAttachmentComparison comparison,
        List<PoValidationResult> validationResults,
        List<PoDuplicateDetection> duplicateDetections,
        List<PoFieldEditHistory> editHistory,
        List<PoApprovalHistory> approvalHistory
) {}
