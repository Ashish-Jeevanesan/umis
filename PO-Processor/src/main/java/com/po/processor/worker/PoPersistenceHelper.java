package com.po.processor.worker;

import com.po.processor.ai.dto.ExtractedLineItemDto;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.ai.dto.FieldEvidenceDto;
import com.po.processor.entity.ExtractedPoLineItem;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.PoBillingDetail;
import com.po.processor.entity.PoFieldEvidence;
import com.po.processor.entity.PoPatientDetail;
import com.po.processor.entity.PoShippingDetail;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPoLineItemRepository;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.PoBillingDetailRepository;
import com.po.processor.repository.PoFieldEvidenceRepository;
import com.po.processor.repository.PoPatientDetailRepository;
import com.po.processor.repository.PoShippingDetailRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Helper component managing transactional persistence of extracted PO entities.
 */
@Component
public class PoPersistenceHelper {

    private final ExtractedPurchaseOrderRepository purchaseOrderRepository;
    private final ExtractedPoLineItemRepository lineItemRepository;
    private final PoPatientDetailRepository patientDetailRepository;
    private final PoBillingDetailRepository billingDetailRepository;
    private final PoShippingDetailRepository shippingDetailRepository;
    private final PoFieldEvidenceRepository fieldEvidenceRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public PoPersistenceHelper(
            ExtractedPurchaseOrderRepository purchaseOrderRepository,
            ExtractedPoLineItemRepository lineItemRepository,
            PoPatientDetailRepository patientDetailRepository,
            PoBillingDetailRepository billingDetailRepository,
            PoShippingDetailRepository shippingDetailRepository,
            PoFieldEvidenceRepository fieldEvidenceRepository
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineItemRepository = lineItemRepository;
        this.patientDetailRepository = patientDetailRepository;
        this.billingDetailRepository = billingDetailRepository;
        this.shippingDetailRepository = shippingDetailRepository;
        this.fieldEvidenceRepository = fieldEvidenceRepository;
    }

    /**
     * Persists the extracted purchase order, line items, patient, billing, and evidence records.
     *
     * @param job Parent ProcessingJob
     * @param dto ExtractedPurchaseOrderDto from AI engine
     * @return Saved ExtractedPurchaseOrder entity
     */
    @Transactional
    public ExtractedPurchaseOrder persistExtractedData(ProcessingJob job, ExtractedPurchaseOrderDto dto) {
        return persistExtractedData(job, dto, null, null);
    }

    /**
     * Persists the extracted purchase order, line items, patient, billing, and evidence records with revision awareness.
     *
     * @param job Parent ProcessingJob
     * @param dto ExtractedPurchaseOrderDto from AI engine
     * @param hospital Resolved canonical Hospital entity
     * @param dupResult Duplicate detection result
     * @return Saved ExtractedPurchaseOrder entity
     */
    @Transactional
    public ExtractedPurchaseOrder persistExtractedData(
            ProcessingJob job,
            ExtractedPurchaseOrderDto dto,
            com.po.processor.entity.Hospital hospital,
            com.po.processor.duplicate.dto.DuplicateCheckResult dupResult
    ) {
        ExtractedPurchaseOrder po = purchaseOrderRepository
                .findFirstByProcessingJobIdOrderByCreatedAtDesc(job.getId())
                .orElseGet(ExtractedPurchaseOrder::new);

        if (po.getId() != null) {
            lineItemRepository.deleteByPurchaseOrderId(po.getId());
            patientDetailRepository.deleteByPurchaseOrderId(po.getId());
            billingDetailRepository.deleteByPurchaseOrderId(po.getId());
            shippingDetailRepository.deleteByPurchaseOrderId(po.getId());
            fieldEvidenceRepository.deleteByPurchaseOrderId(po.getId());
            if (entityManager != null) {
                entityManager.flush();
            }
        }

        po.setProcessingJobId(job.getId());
        po.setTenantId(job.getTenantId());
        po.setPoNumber(dto.poNumber() != null ? dto.poNumber() : "UNKNOWN-" + UUID.randomUUID().toString().substring(0, 8));
        po.setPoTitleDescription(dto.poTitleDescription());
        po.setPoDate(dto.poDate());
        po.setRequestedDeliveryDate(dto.requestedDeliveryDate());
        po.setExpectedDeliveryDate(dto.expectedDeliveryDate());
        po.setCurrency(dto.currency() != null ? dto.currency() : "USD");
        po.setSubtotal(dto.subtotal() != null ? dto.subtotal() : BigDecimal.ZERO);
        po.setDiscount(dto.discount() != null ? dto.discount() : BigDecimal.ZERO);
        po.setFreightCharges(dto.freightCharges() != null ? dto.freightCharges() : BigDecimal.ZERO);
        po.setTaxAmount(dto.taxAmount() != null ? dto.taxAmount() : BigDecimal.ZERO);
        po.setTaxPercentage(dto.taxPercentage());
        po.setGrandTotal(dto.grandTotal() != null ? dto.grandTotal() : BigDecimal.ZERO);
        po.setPaymentTerms(dto.paymentTerms());
        po.setPaymentMethod(dto.paymentMethod());
        po.setDeliveryInstructions(dto.deliveryInstructions());
        po.setIncoterms(dto.incoterms());
        po.setContractReferenceNumber(dto.contractReferenceNumber());
        po.setQuotationReference(dto.quotationReference());
        po.setWorkflowStatus(PoWorkflowStatus.READY_FOR_REVIEW);

        if (dto.vendor() != null && dto.vendor().vendorName() != null && !dto.vendor().vendorName().isBlank()) {
            po.setVendorName(dto.vendor().vendorName());
            po.setVendorAddress(dto.vendor().vendorAddress());
            po.setVendorContact(dto.vendor().vendorContact());
            po.setVendorEmail(dto.vendor().vendorEmail());
            po.setVendorPhone(dto.vendor().vendorPhone());
            po.setVendorTaxId(dto.vendor().vendorTaxId());
            po.setVendorAccountNumber(dto.vendor().vendorAccountNumber());
        } else if (job.getEmailSenderName() != null && !job.getEmailSenderName().isBlank()) {
            String strSender = job.getEmailSenderName();
            if (strSender.contains("<")) {
                strSender = strSender.substring(0, strSender.indexOf('<')).trim();
            }
            po.setVendorName(strSender.isBlank() ? "Unknown Vendor" : strSender);
        }

        po.setHospital(hospital);
        if (dupResult != null && dupResult.isRevision() && dupResult.originalPurchaseOrder() != null) {
            po.setParentPoId(dupResult.originalPurchaseOrder().getId());
            po.setRevisionNumber(dupResult.originalPurchaseOrder().getRevisionNumber() + 1);
            po.setWorkflowStatus(PoWorkflowStatus.NEEDS_REVIEW);
        }

        ExtractedPurchaseOrder savedPo = purchaseOrderRepository.save(po);

        // 1. Line Items
        if (dto.lineItems() != null) {
            for (ExtractedLineItemDto itemDto : dto.lineItems()) {
                ExtractedPoLineItem item = new ExtractedPoLineItem();
                item.setPurchaseOrder(savedPo);
                item.setLineNumber(itemDto.lineNumber());
                item.setProductCode(itemDto.productCode());
                item.setMfgPartNumber(itemDto.mfgPartNumber());
                item.setProductName(itemDto.productName());
                item.setDescription(itemDto.description());
                item.setCategory(itemDto.category());
                item.setQuantity(itemDto.quantity() != null ? itemDto.quantity() : BigDecimal.ONE);
                item.setUnit(itemDto.unit());
                item.setUnitPrice(itemDto.unitPrice() != null ? itemDto.unitPrice() : BigDecimal.ZERO);
                item.setDiscount(itemDto.discount());
                item.setTaxPercentage(itemDto.taxPercentage());
                item.setTaxAmount(itemDto.taxAmount());
                item.setLineSubtotal(itemDto.lineSubtotal());
                item.setLineTotal(itemDto.lineTotal() != null ? itemDto.lineTotal() : BigDecimal.ZERO);
                item.setCurrency(itemDto.currency() != null ? itemDto.currency() : savedPo.getCurrency());
                item.setBatchLotReference(itemDto.batchLotReference());
                item.setSerialNumber(itemDto.serialNumber());
                lineItemRepository.save(item);
            }
        }

        // 2. Patient Details
        if (dto.patient() != null) {
            PoPatientDetail patient = patientDetailRepository
                    .findFirstByPurchaseOrderIdOrderByCreatedAtDesc(savedPo.getId())
                    .orElseGet(() -> new PoPatientDetail(savedPo));
            patient.setPatientName(dto.patient().patientName());
            patient.setPatientIdMrn(dto.patient().patientIdMrn());
            patient.setPatientRefNumber(dto.patient().patientRefNumber());
            patient.setDateOfBirth(dto.patient().dateOfBirth());
            patient.setProcedureCaseReference(dto.patient().procedureCaseReference());
            patient.setSurgeonDoctorName(dto.patient().surgeonDoctorName());
            patient.setDepartmentSpecialty(dto.patient().departmentSpecialty());
            patientDetailRepository.save(patient);
        }

        // 3. Billing Details
        if (dto.billing() != null) {
            PoBillingDetail billing = billingDetailRepository
                    .findFirstByPurchaseOrderIdOrderByCreatedAtDesc(savedPo.getId())
                    .orElseGet(() -> new PoBillingDetail(savedPo));
            billing.setBillingEntity(dto.billing().billingEntity());
            billing.setBillingContact(dto.billing().billingContact());
            billing.setBillingAddress(dto.billing().billingAddress());
            billing.setCity(dto.billing().city());
            billing.setStateRegion(dto.billing().stateRegion());
            billing.setPostalCode(dto.billing().postalCode());
            billing.setCountry(dto.billing().country());
            billing.setTaxId(dto.billing().taxId());
            billingDetailRepository.save(billing);
        }

        // 4. Shipping Details
        if (dto.shipping() != null) {
            PoShippingDetail shipping = shippingDetailRepository
                    .findFirstByPurchaseOrderIdOrderByCreatedAtDesc(savedPo.getId())
                    .orElseGet(() -> new PoShippingDetail(savedPo));
            shipping.setShipToEntity(dto.shipping().shipToEntity());
            shipping.setHospitalLocationName(dto.shipping().hospitalLocationName());
            shipping.setShippingContact(dto.shipping().shippingContact());
            shipping.setShippingAddress(dto.shipping().shippingAddress());
            shipping.setCity(dto.shipping().city());
            shipping.setStateRegion(dto.shipping().stateRegion());
            shipping.setPostalCode(dto.shipping().postalCode());
            shipping.setCountry(dto.shipping().country());
            shipping.setDeliveryInstructions(dto.shipping().deliveryInstructions());
            shipping.setRequestedDeliveryDate(dto.shipping().requestedDeliveryDate());
            shippingDetailRepository.save(shipping);
        }

        // 5. Evidence
        if (dto.fieldEvidence() != null) {
            for (FieldEvidenceDto evDto : dto.fieldEvidence()) {
                PoFieldEvidence ev = new PoFieldEvidence(
                        savedPo, evDto.fieldName(), evDto.extractedValue(), evDto.confidenceScore()
                );
                ev.setNormalizedValue(evDto.normalizedValue());
                ev.setSourceType(evDto.sourceType() != null ? evDto.sourceType() : "ATTACHMENT");
                ev.setSourceAttachmentId(evDto.sourceAttachmentId());
                ev.setSourceFileName(evDto.sourceFileName());
                ev.setSourcePage(evDto.sourcePage());
                ev.setEvidenceText(evDto.evidenceText());
                ev.setInferred(evDto.isInferred());
                fieldEvidenceRepository.save(ev);
            }
        }

        return savedPo;
    }
}
