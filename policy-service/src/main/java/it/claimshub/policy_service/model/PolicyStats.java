package it.claimshub.policy_service.model;

import java.math.BigDecimal;

public record PolicyStats(PolicyType type, PolicyStatus status, long count, BigDecimal totalPremium) {}