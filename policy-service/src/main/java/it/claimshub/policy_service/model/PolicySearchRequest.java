package it.claimshub.policy_service.model;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicySearchRequest(
        PolicyType type,
        PolicyStatus status,
        BigDecimal minPremium,
        BigDecimal maxPremium,
        String holderName,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
        Integer page,
        Integer size,
        PolicySortField sortBy,
        Boolean desc) {

    // i valori predefiniti vivono qui, in un posto solo
    public PolicySearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
        sortBy = sortBy == null ? PolicySortField.ID : sortBy;
        desc = desc == null ? Boolean.FALSE : desc;
    }

    public PolicySearchCriteria toCriteria() {
        return new PolicySearchCriteria(type, status, minPremium, maxPremium,
                holderName, startFrom, startTo);
    }
}