package it.claimshub.claims_service.service;

public class PolicyNotActiveException extends RuntimeException {
    public PolicyNotActiveException(long id, String status) {
        super("Policy " + id + " is not ACTIVE (status: " + status + ")");
    }
}