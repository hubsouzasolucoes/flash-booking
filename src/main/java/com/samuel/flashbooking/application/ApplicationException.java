package com.samuel.flashbooking.application;

public class ApplicationException extends RuntimeException {
    private final ErrorCode code;

    public ApplicationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() { return code; }

    public enum ErrorCode {
        EVENT_NOT_FOUND, RESERVATION_NOT_FOUND, INSUFFICIENT_CAPACITY,
        IDEMPOTENCY_CONFLICT, RESERVATION_NOT_CANCELLABLE
    }
}
