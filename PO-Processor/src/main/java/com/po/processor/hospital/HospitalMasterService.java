package com.po.processor.hospital;

import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.HospitalAlias;
import com.po.processor.hospital.dto.HospitalHistoryDto;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalAliasRepository;
import com.po.processor.repository.HospitalRepository;
import com.po.processor.repository.PoDuplicateDetectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing hospital master resolution, alias matching, and dynamic historical totals.
 */
@Service
public class HospitalMasterService {

    private static final Logger log = LoggerFactory.getLogger(HospitalMasterService.class);

    private final HospitalRepository hospitalRepository;
    private final HospitalAliasRepository hospitalAliasRepository;
    private final ExtractedPurchaseOrderRepository purchaseOrderRepository;
    private final PoDuplicateDetectionRepository duplicateRepository;

    public HospitalMasterService(
            HospitalRepository hospitalRepository,
            HospitalAliasRepository hospitalAliasRepository,
            ExtractedPurchaseOrderRepository purchaseOrderRepository,
            PoDuplicateDetectionRepository duplicateRepository
    ) {
        this.hospitalRepository = hospitalRepository;
        this.hospitalAliasRepository = hospitalAliasRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.duplicateRepository = duplicateRepository;
    }

    /**
     * Resolves an incoming hospital name into a canonical Hospital entity, creating or linking aliases.
     *
     * @param tenantId Tenant identifier
     * @param strRawName Hospital name extracted from document or email
     * @param strHospitalCode Optional hospital identifier code
     * @return Resolved or newly created Hospital entity
     */
    @Transactional
    public Hospital resolveOrCreateHospital(UUID tenantId, String strRawName, String strHospitalCode) {
        if (strRawName == null || strRawName.isBlank()) {
            return resolveFallbackHospital(tenantId);
        }

        String strNormalized = normalizeHospitalName(strRawName);

        // 1. Direct match on normalized name
        Optional<Hospital> optDirect = hospitalRepository.findByTenantIdAndNormalizedName(tenantId, strNormalized);
        if (optDirect.isPresent()) {
            return optDirect.get();
        }

        // 2. Check known aliases
        List<HospitalAlias> alAliases = hospitalAliasRepository.findByAliasNameIgnoreCase(strRawName.trim());
        for (HospitalAlias alias : alAliases) {
            if (alias.getHospital().getTenantId().equals(tenantId)) {
                return alias.getHospital();
            }
        }

        // 3. Create new canonical hospital record
        Hospital newHospital = new Hospital(tenantId, strNormalized);
        newHospital.setLegalName(strRawName.trim());
        newHospital.setHospitalCode(strHospitalCode);
        Hospital savedHospital = hospitalRepository.save(newHospital);

        HospitalAlias canonicalAlias = new HospitalAlias(savedHospital, strRawName.trim(), new BigDecimal("1.00"));
        hospitalAliasRepository.save(canonicalAlias);

        log.info("Created new canonical hospital [{}] for tenant [{}]", strNormalized, tenantId);
        return savedHospital;
    }

    /**
     * Computes lifetime purchasing history for a canonical hospital with dynamic multi-currency aggregation.
     *
     * @param tenantId Tenant identifier
     * @param hospitalId Canonical hospital identifier
     * @return HospitalHistoryDto with currency aggregates and historical PO list
     */
    @Transactional(readOnly = true)
    public HospitalHistoryDto getHospitalHistory(UUID tenantId, UUID hospitalId) {
        Hospital hospital;
        if (tenantId != null) {
            hospital = hospitalRepository.findByIdAndTenantId(hospitalId, tenantId)
                    .orElseThrow(() -> new com.po.processor.exception.ResourceNotFoundException(
                            "Hospital not found for ID: " + hospitalId));
        } else {
            hospital = hospitalRepository.findById(hospitalId)
                    .orElseThrow(() -> new com.po.processor.exception.ResourceNotFoundException(
                            "Hospital not found for ID: " + hospitalId));
        }

        // 1. Dynamic currency aggregation (only approved POs)
        List<ExtractedPurchaseOrderRepository.CurrencySummaryProjection> alAggregates =
                purchaseOrderRepository.findApprovedTotalsByCurrency(tenantId, hospitalId);

        Map<String, BigDecimal> hmCurrencyTotals = new HashMap<>();
        long totalApprovedCount = 0;
        for (ExtractedPurchaseOrderRepository.CurrencySummaryProjection agg : alAggregates) {
            hmCurrencyTotals.put(agg.getCurrency(), agg.getTotalAmount());
            totalApprovedCount += agg.getApprovedCount();
        }

        // 2. Historical purchase orders
        List<ExtractedPurchaseOrder> alPos = purchaseOrderRepository
                .findByTenantIdAndHospitalIdOrderByPoDateDesc(tenantId, hospitalId);

        LocalDate firstDate = null;
        LocalDate latestDate = null;
        List<HospitalHistoryDto.HistoricalPoSummaryDto> alPoSummaries = new ArrayList<>();

        for (ExtractedPurchaseOrder po : alPos) {
            if (po.getPoDate() != null) {
                if (firstDate == null || po.getPoDate().isBefore(firstDate)) {
                    firstDate = po.getPoDate();
                }
                if (latestDate == null || po.getPoDate().isAfter(latestDate)) {
                    latestDate = po.getPoDate();
                }
            }
            alPoSummaries.add(new HospitalHistoryDto.HistoricalPoSummaryDto(
                    po.getId(), po.getPoNumber(), po.getPoDate(), po.getGrandTotal(),
                    po.getCurrency(), po.getVendorName(), po.isApproved()
            ));
        }

        return new HospitalHistoryDto(
                hospital.getId(), hospital.getNormalizedName(), hospital.getLegalName(),
                hospital.getHospitalCode(), totalApprovedCount, hmCurrencyTotals,
                firstDate, latestDate, 0, alPoSummaries
        );
    }

    /**
     * Normalizes a hospital name by stripping facility suffixes and punctuation.
     *
     * @param strName Raw hospital name
     * @return Canonical normalized name
     */
    public String normalizeHospitalName(String strName) {
        if (strName == null) return "unknown-hospital";
        String strClean = strName.toLowerCase(Locale.ROOT)
                .replaceAll("['’]s\\b", "")
                .replaceAll("[.,'\"#()\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        strClean = strClean.replaceAll("(?i)\\b(childrens|children|research|memorial|general|university|county|community|regional|specialty)\\b", "");
        strClean = strClean.replaceAll("(?i)\\b(hospital|hosp|clinic|medical center|health system|healthcare|foundation)\\b", "");
        strClean = strClean.replaceAll("\\s+", " ").trim();

        if (strClean.isBlank()) {
            return strName.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "-");
        }
        return strClean.replaceAll("\\s+", "-");
    }

    private Hospital resolveFallbackHospital(UUID tenantId) {
        String strFallback = "general-hospital";
        return hospitalRepository.findByTenantIdAndNormalizedName(tenantId, strFallback)
                .orElseGet(() -> hospitalRepository.save(new Hospital(tenantId, strFallback)));
    }
}
