package it.claimshub.claims_service.service;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.model.*;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import it.claimshub.claims_service.port.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaimQueryService {

    private final ClaimRepository repository;
    private final ClaimHistoryStore history;
    private final PolicyClient policyClient;

    public Mono<Claim> findById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ClaimNotFoundException(id)));
    }

    public Flux<ClaimEvent> history(String id) {
        // Flux.defer: la lettura dello storico parte solo se il sinistro esiste
        return findById(id).thenMany(Flux.defer(() -> history.findByClaimId(id)));
    }

    public Mono<PageResult<Claim>> search(ClaimSearchCriteria criteria, int page, int size,
                                          ClaimSortField sortBy, boolean desc) {
        if (page < 0 || size < 1 || size > 100) {
            return Mono.error(new InvalidSearchException("page must be >= 0 and size between 1 and 100"));
        }
        if (criteria.minAmount() != null && criteria.maxAmount() != null
                && criteria.minAmount().compareTo(criteria.maxAmount()) > 0) {
            return Mono.error(new InvalidSearchException("minAmount must not exceed maxAmount"));
        }
        if (criteria.createdFrom() != null && criteria.createdTo() != null
                && criteria.createdFrom().isAfter(criteria.createdTo())) {
            return Mono.error(new InvalidSearchException("createdFrom must not be after createdTo"));
        }
        return repository.search(criteria, page, size, sortBy, desc);
    }

    public Mono<PolicySummary> policySummary(long policyId) {
        Mono<List<Claim>> claims = repository.findByPolicyId(policyId).collectList();
        return Mono.zip(policyClient.getPolicy(policyId), claims)
                .map(t -> new PolicySummary(t.getT1(), stats(t.getT2())));
    }

    private static PolicySummary.ClaimStats stats(List<Claim> claims) {
        BigDecimal total = claims.stream()
                .map(Claim::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus s : ClaimStatus.values()) {
            byStatus.put(s, 0L);              // anche gli stati vuoti compaiono, a zero
        }
        claims.forEach(c -> byStatus.merge(c.status(), 1L, Long::sum));
        return new PolicySummary.ClaimStats(claims.size(), total, byStatus);
    }
}