package it.claimshub.claims_service.model;

import java.time.Instant;

public record ClaimEvent(String claimId, Instant timestamp, String type,
                         String detail, String actor) {}