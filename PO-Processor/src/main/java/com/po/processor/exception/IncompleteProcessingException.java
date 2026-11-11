package com.po.processor.exception;

/**
 * Exception thrown when a user attempts to review, verify, or approve a PO while processing is still underway.
 */
public class IncompleteProcessingException extends RuntimeException {

    private final int progressPercentage;
    private final String currentStage;

    public IncompleteProcessingException(String strMessage, int progressPercentage, String strStage) {
        super(strMessage);
        this.progressPercentage = progressPercentage;
        this.currentStage = strStage;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public String getCurrentStage() {
        return currentStage;
    }
}
