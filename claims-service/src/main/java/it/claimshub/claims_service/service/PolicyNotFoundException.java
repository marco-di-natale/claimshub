package it.claimshub.claims_service.service;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(long id) { super("Policy not found: " + id); }
}