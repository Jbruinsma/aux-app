package com.aux_app.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aux_app.dto.base.AuxServerError;
import com.aux_app.dto.base.ErrorDetails;

// Every error response in the app goes through here, so they all share the AuxServerError shape.
@RestControllerAdvice
public class AuxErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(AuxErrorHandler.class);

    @ExceptionHandler(AuxException.class)
    public ResponseEntity<AuxServerError> handleAux(AuxException e) {
        return respond(e.getStatus(), e.getDetails());
    }

    // @Valid on a @RequestBody failed
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuxServerError> handleValidation(MethodArgumentNotValidException e) {
        FieldError field = e.getBindingResult().getFieldError();
        String parameter = field == null ? null : field.getField();
        String message = field == null ? "Invalid request" : parameter + " " + field.getDefaultMessage();
        return respond(HttpStatus.BAD_REQUEST, new ErrorDetails(
                HttpStatus.BAD_REQUEST.getReasonPhrase(), "INVALID_FIELD", message, parameter));
    }

    // Missing or malformed JSON body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuxServerError> handleUnreadable(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorDetails(
                HttpStatus.BAD_REQUEST.getReasonPhrase(), "MALFORMED_BODY", "Request body is missing or not valid JSON", null));
    }

    // UNIQUE index hit, e.g. two signups racing for the same username. Hibernate's SQLite dialect
    // reports it as a generic JpaSystemException, so check the driver's error code too
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AuxServerError> handleDataAccess(DataAccessException e) {
        boolean unique = e instanceof DataIntegrityViolationException
                || (e.getMostSpecificCause() instanceof SQLiteException sqlite
                    && sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE);
        if (!unique) return handleUnexpected(e);
        return respond(HttpStatus.CONFLICT, new ErrorDetails(
                HttpStatus.CONFLICT.getReasonPhrase(), "CONFLICT", "That value is already in use", null));
    }

    // Everything else. Spring's own errors (404 no route, 405 wrong method, ...) implement ErrorResponse
    // and keep their status; anything left is a bug, so log it but never leak the stack trace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuxServerError> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse framework) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            return respond(status, new ErrorDetails(
                    status.getReasonPhrase(), "REQUEST_FAILED", framework.getBody().getDetail(), null));
        }
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorDetails(
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "INTERNAL_ERROR", "Something went wrong", null));
    }

    private static ResponseEntity<AuxServerError> respond(HttpStatus status, ErrorDetails details) {
        return ResponseEntity.status(status).body(new AuxServerError(details));
    }
}
