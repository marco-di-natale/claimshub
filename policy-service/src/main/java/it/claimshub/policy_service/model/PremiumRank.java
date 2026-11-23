package it.claimshub.policy_service.model;

import java.math.BigDecimal;

public record PremiumRank(String policyNumber, PolicyType type, BigDecimal premium,
                          int rank, BigDecimal sharePercent) {}