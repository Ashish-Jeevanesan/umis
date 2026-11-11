package com.po.processor.exception;

/**
 * Exception thrown when a Gemini response was truncated due to token limits or incomplete JSON.
 */
public class GeminiResponseTruncatedException extends RuntimeException {

    private final String finishReason;

    public GeminiResponseTruncatedException(String strMessage, String strFinishReason) {
        super(strMessage);
        this.finishReason = strFinishReason;
    }

    public String getFinishReason() {
        return finishReason;
    }
}
