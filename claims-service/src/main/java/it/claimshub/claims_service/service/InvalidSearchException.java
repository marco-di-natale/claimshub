package it.claimshub.claims_service.service;

public class InvalidSearchException extends RuntimeException {
    public InvalidSearchException(String message) { super(message); }
}