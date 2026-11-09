package it.claimshub.claims_service.service;

public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String message) { super(message); }
}