package it.claimshub.claims_service.model;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull ClaimStatus status) {}