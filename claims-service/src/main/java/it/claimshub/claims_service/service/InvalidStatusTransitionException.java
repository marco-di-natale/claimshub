package it.claimshub.claims_service.service;

import it.claimshub.claims_service.model.ClaimStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Transition not allowed: " + from + " -> " + to);
    }
}