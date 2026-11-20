package it.claimshub.policy_service.model;

public enum PolicyStatus {
    ACTIVE, EXPIRED, CANCELLED;

    public boolean canBeCancelled() { return this == ACTIVE; }

    public boolean canBeRenewed() { return this != CANCELLED; }
}
