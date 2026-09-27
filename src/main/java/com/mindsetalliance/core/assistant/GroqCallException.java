package com.mindsetalliance.core.assistant;

final class GroqCallException extends RuntimeException {

    private final int httpStatus;
    private final String groqBody;
    private final String reasonCode;

    GroqCallException(int httpStatus, String groqBody, String reasonCode, String logMessage) {
        super(logMessage);
        this.httpStatus = httpStatus;
        this.groqBody = groqBody == null ? "" : groqBody;
        this.reasonCode = reasonCode;
    }

    static GroqCallException missingKey() {
        return new GroqCallException(0, "", "missing_key", "GROQ_API_KEY absente");
    }

    int httpStatus() {
        return httpStatus;
    }

    String groqBody() {
        return groqBody;
    }

    String reasonCode() {
        return reasonCode;
    }
}
