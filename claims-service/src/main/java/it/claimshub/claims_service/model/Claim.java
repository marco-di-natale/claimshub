package it.claimshub.claims_service.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Claim(String id, long policyId, String description, BigDecimal amount,
                    ClaimStatus status, Instant createdAt, String createdBy) {

    public Claim withStatus(ClaimStatus newStatus) {
        return new Claim(id, policyId, description, amount, newStatus, createdAt, createdBy);
    }
}