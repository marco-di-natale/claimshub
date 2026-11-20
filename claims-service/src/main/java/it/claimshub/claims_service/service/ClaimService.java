package it.claimshub.claims_service.service;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.model.*;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import it.claimshub.claims_service.port.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final PolicyClient policyClient;

    private final ClaimRepository repository;
    private final ClaimHistoryStore history;
    private final ClaimAudit audit;

    public Mono<Claim> create(CreateClaimRequest request, String username) {
        return policyClient.getPolicy(request.policyId())
                .flatMap(policy -> {
                    if (!"ACTIVE".equals(policy.status())) {
                        return Mono.error(new PolicyNotActiveException(
                                request.policyId(), policy.status()));
                    }
                    Claim claim = new Claim(UUID.randomUUID().toString(), request.policyId(),
                            request.description(), request.amount(), ClaimStatus.OPEN,
                            Instant.now(), username);
                    return repository.save(claim)
                            .flatMap(saved -> audit.record(saved.id(), "CREATED", "Claim opened", username)
                                    .thenReturn(saved));
                });
    }

    public Mono<Claim> findById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ClaimNotFoundException(id)));
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

    public Mono<Claim> changeStatus(String id, ClaimStatus newStatus, String username) {
        return findById(id).flatMap(current -> {
            if (!current.status().canTransitionTo(newStatus)) {
                return Mono.error(new InvalidStatusTransitionException(current.status(), newStatus));
            }
            return repository.save(current.withStatus(newStatus))
                    .flatMap(saved -> audit.record(saved.id(), "STATUS_CHANGED", current.status() + " -> " + newStatus, username)
                            .thenReturn(saved));
        });
    }

    public Flux<ClaimEvent> history(String id) {
        return findById(id).thenMany(history.findByClaimId(id));
    }

}