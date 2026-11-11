package com.po.processor.entity;

/**
 * Business purchase-order workflow statuses representing the human review, revision, and approval lifecycle.
 */
public enum PoWorkflowStatus {
    DRAFT,
    READY_FOR_REVIEW,
    NEEDS_REVIEW,
    EDITED,
    VERIFIED,
    APPROVED,
    SUPERSEDED,
    REJECTED,
    CANCELLED
}
