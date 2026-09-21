package com.aux.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aux.dto.base.AuxServerError;
import com.aux.dto.base.ErrorDetails;

// Every error response in the app goes through here, so they all share the AuxServerError shape.
@RestControllerAdvice
public class AuxErrorHandler {

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

    private static ResponseEntity<AuxServerError> respond(HttpStatus status, ErrorDetails details) {
        return ResponseEntity.status(status).body(new AuxServerError(details));
    }
}
