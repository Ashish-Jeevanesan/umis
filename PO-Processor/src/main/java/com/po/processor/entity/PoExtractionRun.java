package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an AI model execution attempt and token telemetry.
 */
@Entity
@Table(name = "po_extraction_runs", schema = "po_processor")
public class PoExtractionRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "processing_job_id", nullable = false)
    private UUID processingJobId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "raw_response_json", columnDefinition = "JSONB")
    private String rawResponseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoExtractionRun() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoExtractionRun(UUID processingJobId, String strModelName, String strPromptVersion) {
        this();
        this.processingJobId = processingJobId;
        this.modelName = strModelName;
        this.promptVersion = strPromptVersion;
        this.status = "INITIATED";
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProcessingJobId() { return processingJobId; }
    public void setProcessingJobId(UUID processingJobId) { this.processingJobId = processingJobId; }
    public String getModelName() { return modelName; }
    public void setModelName(String strModelName) { this.modelName = strModelName; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String strPromptVersion) { this.promptVersion = strPromptVersion; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }
    public String getStatus() { return status; }
    public void setStatus(String strStatus) { this.status = strStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String strErrorMessage) { this.errorMessage = strErrorMessage; }
    public String getRawResponseJson() { return rawResponseJson; }
    public void setRawResponseJson(String strRawResponseJson) { this.rawResponseJson = strRawResponseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
