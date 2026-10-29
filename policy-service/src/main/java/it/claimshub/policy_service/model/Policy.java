package it.claimshub.policy_service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Policy(Long id, String policyNumber, String holderName,
                     PolicyType type, PolicyStatus status,
                     BigDecimal premium, LocalDate startDate, LocalDate endDate) {}
