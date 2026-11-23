package it.claimshub.claims_service.model;

import it.claimshub.claims_service.client.PolicyClient;

import java.math.BigDecimal;
import java.util.Map;

public record PolicySummary(PolicyClient.PolicyInfo policy, ClaimStats claims) {

    public record ClaimStats(long total, BigDecimal totalAmount, Map<ClaimStatus, Long> byStatus) {}
}