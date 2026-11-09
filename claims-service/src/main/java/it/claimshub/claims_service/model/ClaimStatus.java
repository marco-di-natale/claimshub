package it.claimshub.claims_service.model;

public enum ClaimStatus {
    OPEN, IN_REVIEW, APPROVED, REJECTED;

    public boolean canTransitionTo(ClaimStatus next) {
        return switch (this) {
            case OPEN -> next == IN_REVIEW;
            case IN_REVIEW -> next == APPROVED || next == REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }
}