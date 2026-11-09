package it.claimshub.claims_service.service;

public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException(long maxBytes) {
        super("File too large (max " + maxBytes / (1024 * 1024) + " MB)");
    }
}