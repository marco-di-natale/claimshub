package it.claimshub.policy_service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicySearchCriteria(PolicyType type, PolicyStatus status,
                                   BigDecimal minPremium, BigDecimal maxPremium,
                                   String holderName,
                                   LocalDate startFrom, LocalDate startTo) {}