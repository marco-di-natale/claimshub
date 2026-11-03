package it.claimshub.claims_service.service;

public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(String id) { super("Claim not found: " + id); }
}