package it.claimshub.policy_service.model;

import java.math.BigDecimal;

public record PremiumByType(PolicyType type, long policies, BigDecimal totalPremium,
                            BigDecimal averagePremium, BigDecimal minPremium, BigDecimal maxPremium) {}