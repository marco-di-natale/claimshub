package it.claimshub.policy_service.model;

import java.math.BigDecimal;

public record MonthlyNewPolicies(int year, int month, long policies,
                                 BigDecimal premium, BigDecimal cumulativePremium) {}