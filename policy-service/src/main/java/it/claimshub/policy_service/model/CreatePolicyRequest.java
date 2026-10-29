package it.claimshub.policy_service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePolicyRequest(
        @NotBlank String policyNumber,
        @NotBlank String holderName,
        @NotNull PolicyType type,
        @NotNull @Positive BigDecimal premium,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {

}