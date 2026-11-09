package it.claimshub.claims_service.service;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String id) { super("Document not found: " + id); }
}