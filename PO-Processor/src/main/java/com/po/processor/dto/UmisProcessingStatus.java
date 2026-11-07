package com.po.processor.dto;

/**
 * Processing status reported to UMIS acknowledgement endpoint.
 */
public enum UmisProcessingStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    REVIEW_REQUIRED,
    IGNORED
}
