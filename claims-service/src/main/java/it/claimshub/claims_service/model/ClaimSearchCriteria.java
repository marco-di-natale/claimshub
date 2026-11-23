package it.claimshub.claims_service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record ClaimSearchCriteria(ClaimStatus status, Long policyId,
                                  BigDecimal minAmount, BigDecimal maxAmount,
                                  LocalDate createdFrom, LocalDate createdTo) {

    // ogni campo null significa "non filtrare per questo"
    public boolean matches(Claim claim) {
        LocalDate created = claim.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
        return (status == null || claim.status() == status)
                && (policyId == null || policyId.equals(claim.policyId()))
                && (minAmount == null || claim.amount().compareTo(minAmount) >= 0)
                && (maxAmount == null || claim.amount().compareTo(maxAmount) <= 0)
                && (createdFrom == null || !created.isBefore(createdFrom))
                && (createdTo == null || !created.isAfter(createdTo));
    }
}