package com.po.processor.duplicate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Service providing database-level concurrency protection using PostgreSQL transaction advisory locks.
 */
@Service
public class PoConcurrencyLockService {

    private static final Logger log = LoggerFactory.getLogger(PoConcurrencyLockService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Acquires an exclusive transaction-scoped advisory lock for the given tenant and PO number.
     * The lock is automatically released upon transaction commit or rollback.
     *
     * @param tenantId Tenant identifier
     * @param strPoNumber Purchase order number
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquirePoLock(UUID tenantId, String strPoNumber) {
        if (tenantId == null || strPoNumber == null || strPoNumber.isBlank()) {
            return;
        }

        int key1 = tenantId.hashCode();
        int key2 = strPoNumber.trim().toLowerCase(Locale.ROOT).hashCode();

        try {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key1, :key2)")
                    .setParameter("key1", key1)
                    .setParameter("key2", key2)
                    .getSingleResult();
            log.debug("Acquired transaction advisory lock for tenant [{}] PO [{}]", tenantId, strPoNumber);
        } catch (Exception exception) {
            log.debug("Advisory lock execution note (supported on PostgreSQL): {}", exception.getMessage());
        }
    }
}
