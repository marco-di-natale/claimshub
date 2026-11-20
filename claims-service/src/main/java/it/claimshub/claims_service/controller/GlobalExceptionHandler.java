package it.claimshub.claims_service.controller;

import it.claimshub.claims_service.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(Instant timestamp, int status, String error, String message) {}

    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ClaimNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiError> conflict(InvalidStatusTransitionException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> validation(WebExchangeBindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> badInput(ServerWebInputException e) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body or parameter");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }

    @ExceptionHandler(PolicyNotFoundException.class)
    public ResponseEntity<ApiError> policyNotFound(PolicyNotFoundException e) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(PolicyNotActiveException.class)
    public ResponseEntity<ApiError> policyNotActive(PolicyNotActiveException e) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(PolicyServiceException.class)
    public ResponseEntity<ApiError> policyServiceDown(PolicyServiceException e) {
        return build(HttpStatus.BAD_GATEWAY, "Policy service not available");
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiError> documentNotFound(DocumentNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<ApiError> invalidDocument(InvalidDocumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DocumentTooLargeException.class)
    public ResponseEntity<ApiError> tooLarge(DocumentTooLargeException e) {
        return build(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(InvalidSearchException.class)
    public ResponseEntity<ApiError> invalidSearch(InvalidSearchException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}