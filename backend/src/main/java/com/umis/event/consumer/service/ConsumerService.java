package com.umis.event.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umis.common.exception.ConflictException;
import com.umis.common.exception.ResourceNotFoundException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.email.model.EmailMessage;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.event.consumer.dto.ConsumerRegistrationDto;
import com.umis.event.consumer.dto.CreateConsumerRequest;
import com.umis.event.consumer.dto.ProcessingAcknowledgementRequest;
import com.umis.event.consumer.dto.ProcessingStatusDto;
import com.umis.event.consumer.model.ConsumerRegistration;
import com.umis.event.consumer.model.EmailProcessingStatus;
import com.umis.event.consumer.repository.ConsumerRegistrationRepository;
import com.umis.event.consumer.repository.EmailProcessingStatusRepository;
import com.umis.event.model.EventOutbox;
import com.umis.event.model.UmisEvent;
import com.umis.event.repository.EventOutboxRepository;
import com.umis.security.TenantContext;
import com.umis.tenant.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service managing downstream consumer registration and independent email processing acknowledgements.
 */
@Service
public class ConsumerService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    private final ConsumerRegistrationRepository consumerRepository;
    private final EmailProcessingStatusRepository statusRepository;
    private final EmailMessageRepository messageRepository;
    private final EventOutboxRepository outboxRepository;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;

    public ConsumerService(
            ConsumerRegistrationRepository consumerRepository,
            EmailProcessingStatusRepository statusRepository,
            EmailMessageRepository messageRepository,
            EventOutboxRepository outboxRepository,
            TenantService tenantService,
            ObjectMapper objectMapper
    ) {
        this.consumerRepository = consumerRepository;
        this.statusRepository = statusRepository;
        this.messageRepository = messageRepository;
        this.outboxRepository = outboxRepository;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConsumerRegistrationDto registerConsumer(CreateConsumerRequest request) {
        UUID effectiveTenantId = resolveEffectiveTenantId(request.tenantId());
        if (effectiveTenantId != null) {
            tenantService.validateTenantAccess(effectiveTenantId);
        }

        if (consumerRepository.existsByTenantIdAndConsumerCode(effectiveTenantId, request.consumerCode())) {
            throw new ConflictException("Consumer code '" + request.consumerCode() + "' is already registered");
        }

        ConsumerRegistration consumer = new ConsumerRegistration(
                effectiveTenantId, request.consumerCode(), request.displayName(), request.description());
        if (StringUtils.hasText(request.subscribedEvents())) {
            consumer.setSubscribedEvents(request.subscribedEvents());
        }

        return ConsumerRegistrationDto.from(consumerRepository.save(consumer));
    }

    @Transactional(readOnly = true)
    public Page<ConsumerRegistrationDto> listConsumers(UUID tenantId, Pageable pageable) {
        UUID effectiveTenantId = resolveEffectiveTenantId(tenantId);
        return consumerRepository.findByTenantIdOrTenantIdIsNull(effectiveTenantId, pageable)
                .map(ConsumerRegistrationDto::from);
    }

    @Transactional
    public ProcessingStatusDto acknowledgeProcessing(UUID emailId, ProcessingAcknowledgementRequest request) {
        EmailMessage email = messageRepository.findById(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Email message not found with ID: " + emailId));
        tenantService.validateTenantAccess(email.getTenantId());

        EmailProcessingStatus status = statusRepository
                .findByEmailIdAndConsumerCode(emailId, request.consumerModule())
                .orElseGet(() -> new EmailProcessingStatus(email, request.consumerModule(), request.status()));

        if (StringUtils.hasText(request.idempotencyKey())
                && request.idempotencyKey().equals(status.getAcknowledgementIdempotencyKey())) {
            log.info("Idempotent acknowledgement received for email {} from consumer {}", emailId, request.consumerModule());
            return ProcessingStatusDto.from(status);
        }

        status.setStatus(request.status());
        status.setReferenceId(request.referenceId());
        status.setMessage(request.message());
        status.setProcessedAt(request.processedAt() != null ? request.processedAt() : Instant.now());
        status.setAcknowledgementIdempotencyKey(request.idempotencyKey());

        EmailProcessingStatus savedStatus = statusRepository.save(status);

        emitProcessingEvent(email, request);

        return ProcessingStatusDto.from(savedStatus);
    }

    @Transactional(readOnly = true)
    public List<ProcessingStatusDto> getProcessingStatuses(UUID emailId) {
        EmailMessage email = messageRepository.findById(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found: " + emailId));
        tenantService.validateTenantAccess(email.getTenantId());

        return statusRepository.findByEmailId(emailId).stream()
                .map(ProcessingStatusDto::from)
                .toList();
    }

    private void emitProcessingEvent(EmailMessage email, ProcessingAcknowledgementRequest request) {
        try {
            String eventType = switch (request.status()) {
                case SUCCESS -> "EMAIL_PROCESSING_COMPLETED";
                case FAILED -> "EMAIL_PROCESSING_FAILED";
                default -> "EMAIL_PROCESSING_STATUS_UPDATED";
            };

            UmisEvent<ProcessingAcknowledgementRequest> event = UmisEvent.of(
                    eventType, email.getTenantId(), email.getMailbox().getId(), request);
            String payloadJson = objectMapper.writeValueAsString(event);

            EventOutbox outbox = new EventOutbox(
                    event.eventId(), eventType, "EMAIL_PROCESSING", email.getId().toString(), email.getTenantId(), payloadJson);
            outboxRepository.save(outbox);
        } catch (Exception exception) {
            log.warn("Failed to create outbox event for processing acknowledgement: {}", exception.getMessage());
        }
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
