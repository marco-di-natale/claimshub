package it.claimshub.claims_service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateClaimRequest(
        @NotNull @Positive Long policyId,
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount) {}