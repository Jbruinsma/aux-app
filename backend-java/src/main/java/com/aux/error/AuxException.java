package com.aux.error;

import org.springframework.http.HttpStatus;

import com.aux.dto.base.ErrorDetails;

// Throw from any controller; AuxErrorHandler turns it into an AuxServerError response.
public class AuxException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorDetails details;

    public AuxException(HttpStatus status, String code, String message, String parameter) {
        super(message);
        this.status = status;
        this.details = new ErrorDetails(status.getReasonPhrase(), code, message, parameter);
    }

    public AuxException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public HttpStatus getStatus() { return status; }
    public ErrorDetails getDetails() { return details; }
}
