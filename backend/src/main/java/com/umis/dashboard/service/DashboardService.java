package com.umis.dashboard.service;

import com.umis.common.exception.BadRequestException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.dashboard.dto.DailyTrendDto;
import com.umis.dashboard.dto.GlobalDashboardDto;
import com.umis.dashboard.dto.ProviderDistributionDto;
import com.umis.dashboard.dto.SystemHealthDto;
import com.umis.dashboard.dto.TenantDashboardDto;
import com.umis.dashboard.model.DailyIngestionMetric;
import com.umis.dashboard.repository.DailyIngestionMetricRepository;
import com.umis.email.model.IngestionStatus;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.event.consumer.model.ProcessingStatus;
import com.umis.event.consumer.repository.EmailProcessingStatusRepository;
import com.umis.event.model.OutboxStatus;
import com.umis.event.repository.EventOutboxRepository;
import com.umis.mailbox.dto.MailboxDto;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.MailboxStatus;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.security.TenantContext;
import com.umis.tenant.dto.TenantDto;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.model.TenantStatus;
import com.umis.tenant.repository.TenantRepository;
import com.umis.tenant.service.TenantService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service calculating aggregated operational metrics and health status for dashboards.
 */
@Service
public class DashboardService {

    private final TenantRepository tenantRepository;
    private final MailboxConnectionRepository mailboxRepository;
    private final EmailMessageRepository emailRepository;
    private final EmailProcessingStatusRepository processingStatusRepository;
    private final EventOutboxRepository outboxRepository;
    private final DailyIngestionMetricRepository metricRepository;
    private final com.umis.email.repository.IngestionJobRepository jobRepository;
    private final TenantService tenantService;
    private final com.umis.config.UmisProperties properties;
    private final org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory;

    public DashboardService(
            TenantRepository tenantRepository,
            MailboxConnectionRepository mailboxRepository,
            EmailMessageRepository emailRepository,
            EmailProcessingStatusRepository processingStatusRepository,
            EventOutboxRepository outboxRepository,
            DailyIngestionMetricRepository metricRepository,
            com.umis.email.repository.IngestionJobRepository jobRepository,
            TenantService tenantService,
            com.umis.config.UmisProperties properties,
            org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory
    ) {
        this.tenantRepository = tenantRepository;
        this.mailboxRepository = mailboxRepository;
        this.emailRepository = emailRepository;
        this.processingStatusRepository = processingStatusRepository;
        this.outboxRepository = outboxRepository;
        this.metricRepository = metricRepository;
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public GlobalDashboardDto getGlobalDashboard() {
        long totalTenants = tenantRepository.count();
        long activeTenants = tenantRepository.countByStatus(TenantStatus.ACTIVE);
        long mailboxes = mailboxRepository.count();
        long emailsIngested = emailRepository.count();
        long emailsDiscovered = jobRepository.sumAllItemsDiscovered();
        if (emailsDiscovered < emailsIngested) {
            emailsDiscovered = emailsIngested;
        }
        long emailsFailed = emailRepository.countByIngestionStatus(IngestionStatus.FAILED)
                + jobRepository.sumAllItemsFailed()
                + processingStatusRepository.countByStatus(ProcessingStatus.FAILED);
        long parsedSuccess = processingStatusRepository.countByStatus(ProcessingStatus.SUCCESS);
        long outboxPublished = outboxRepository.countByStatus(OutboxStatus.PUBLISHED);

        List<ProviderDistributionDto> providerDist = calculateProviderDistribution();
        List<TenantDto> recentTenants = tenantRepository.findAll(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent().stream().map(TenantDto::from).toList();

        List<DailyTrendDto> trends = getDailyTrends(null, 7);

        return new GlobalDashboardDto(
                totalTenants, activeTenants, mailboxes,
                emailsDiscovered, emailsIngested, outboxPublished,
                parsedSuccess, emailsFailed, providerDist, recentTenants, trends
        );
    }

    @Transactional(readOnly = true)
    public TenantDashboardDto getTenantDashboard(UUID tenantId) {
        UUID resolvedId = resolveEffectiveTenantId(tenantId);
        if (resolvedId == null) {
            resolvedId = tenantRepository.findAll().stream().findFirst().map(Tenant::getId).orElse(null);
            if (resolvedId == null) {
                throw new BadRequestException("No tenant available in system");
            }
        }
        final UUID effectiveTenantId = resolvedId;
        tenantService.validateTenantAccess(effectiveTenantId);

        Tenant tenant = tenantRepository.findById(effectiveTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + effectiveTenantId));

        long mailboxesCount = mailboxRepository.countByTenantId(effectiveTenantId);
        long emailsIngested = emailRepository.countByTenantId(effectiveTenantId);
        long emailsDiscovered = jobRepository.sumItemsDiscoveredByTenantId(effectiveTenantId);
        if (emailsDiscovered < emailsIngested) {
            emailsDiscovered = emailsIngested;
        }
        long emailsPublished = outboxRepository.countByTenantIdAndStatus(effectiveTenantId, OutboxStatus.PUBLISHED);
        long emailsFailed = emailRepository.countByTenantIdAndIngestionStatus(effectiveTenantId, IngestionStatus.FAILED)
                + jobRepository.sumItemsFailedByTenantId(effectiveTenantId)
                + processingStatusRepository.countByTenantIdAndStatus(effectiveTenantId, ProcessingStatus.FAILED);
        long parsedSuccess = processingStatusRepository.countByTenantIdAndStatus(
                effectiveTenantId, ProcessingStatus.SUCCESS);
        long parsedPending = processingStatusRepository.countByTenantIdAndStatus(
                effectiveTenantId, ProcessingStatus.PENDING);

        List<MailboxDto> mailboxes = mailboxRepository.findByTenantId(
                effectiveTenantId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent().stream().map(MailboxDto::from).toList();

        List<DailyTrendDto> trends = getDailyTrends(effectiveTenantId, 7);

        return new TenantDashboardDto(
                effectiveTenantId, tenant.getName(), mailboxesCount,
                emailsDiscovered, emailsIngested, emailsIngested, emailsPublished,
                parsedSuccess, parsedPending, emailsFailed, mailboxes, trends
        );
    }

    @Transactional(readOnly = true)
    public List<DailyTrendDto> getDailyTrends(UUID tenantId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days > 0 ? days : 7);

        List<DailyIngestionMetric> storedMetrics = (tenantId != null)
                ? metricRepository.findByTenantIdAndMetricDateBetweenOrderByMetricDateAsc(tenantId, startDate, endDate)
                : metricRepository.findByMetricDateBetweenOrderByMetricDateAsc(startDate, endDate);

        Map<LocalDate, DailyTrendDto> trendMap = new HashMap<>();
        for (DailyIngestionMetric metric : storedMetrics) {
            DailyTrendDto current = trendMap.get(metric.getMetricDate());
            if (current == null) {
                trendMap.put(metric.getMetricDate(), new DailyTrendDto(
                        metric.getMetricDate(),
                        metric.getEmailsDiscovered(),
                        metric.getEmailsIngested(),
                        metric.getEmailsPublished(),
                        metric.getEmailsParsedSuccess(),
                        metric.getEmailsFailed()
                ));
            } else {
                trendMap.put(metric.getMetricDate(), new DailyTrendDto(
                        metric.getMetricDate(),
                        current.discovered() + metric.getEmailsDiscovered(),
                        current.ingested() + metric.getEmailsIngested(),
                        current.published() + metric.getEmailsPublished(),
                        current.parsedSuccess() + metric.getEmailsParsedSuccess(),
                        current.failed() + metric.getEmailsFailed()
                ));
            }
        }

        List<DailyTrendDto> results = new ArrayList<>();
        for (int dayOffset = days; dayOffset >= 0; dayOffset--) {
            LocalDate date = endDate.minusDays(dayOffset);
            results.add(trendMap.getOrDefault(date, new DailyTrendDto(date, 0, 0, 0, 0, 0)));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public SystemHealthDto getSystemHealth() {
        long pendingOutbox = outboxRepository.countByStatus(OutboxStatus.PENDING);
        long failedMailboxes = mailboxRepository.countByStatus(MailboxStatus.CONNECTION_ERROR);
        boolean rabbitConnected = checkRabbitMqConnected();
        boolean isLocalStorage = !"S3".equalsIgnoreCase(properties.getStorage().getType());

        String status = (failedMailboxes > 0 || !rabbitConnected) ? "DEGRADED" : "UP";

        Map<String, Object> details = new HashMap<>();
        details.put("database", "CONNECTED");
        details.put("rabbitmq", rabbitConnected ? "UP" : "DOWN");
        details.put("storage", isLocalStorage ? "LOCAL_FILESYSTEM_READY" : "S3_MINIO_READY");

        return new SystemHealthDto(
                status, Instant.now(), true, rabbitConnected, true,
                pendingOutbox, failedMailboxes, details
        );
    }

    private boolean checkRabbitMqConnected() {
        try (org.springframework.amqp.rabbit.connection.Connection connection = rabbitConnectionFactory.createConnection()) {
            return connection.isOpen();
        } catch (Exception exception) {
            return false;
        }
    }

    private List<ProviderDistributionDto> calculateProviderDistribution() {
        Map<MailboxProvider, Long> counts = new EnumMap<>(MailboxProvider.class);
        for (MailboxConnection conn : mailboxRepository.findAll()) {
            counts.put(conn.getProvider(), counts.getOrDefault(conn.getProvider(), 0L) + 1);
        }

        List<ProviderDistributionDto> list = new ArrayList<>();
        for (Map.Entry<MailboxProvider, Long> entry : counts.entrySet()) {
            list.add(new ProviderDistributionDto(entry.getKey(), entry.getKey().name(), entry.getValue(), 0));
        }
        return list;
    }

    private UUID resolveEffectiveTenantId(UUID requestedTenantId) {
        UUID contextId = TenantContext.getTenantId();
        if (contextId != null) {
            if (requestedTenantId != null && !requestedTenantId.equals(contextId)) {
                throw new UnauthorizedException("Access denied: Cannot specify or access another tenant: " + requestedTenantId);
            }
            return contextId;
        }
        return requestedTenantId;
    }
}
