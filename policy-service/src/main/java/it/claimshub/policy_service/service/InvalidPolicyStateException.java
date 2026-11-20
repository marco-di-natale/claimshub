package it.claimshub.policy_service.service;

public class InvalidPolicyStateException extends RuntimeException {
    public InvalidPolicyStateException(String message) { super(message); }
}