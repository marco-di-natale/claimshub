package it.claimshub.claims_service.port;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimSearchCriteria;
import it.claimshub.claims_service.model.ClaimSortField;
import it.claimshub.claims_service.model.PageResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClaimRepository {
    Mono<Claim> save(Claim claim);
    Mono<Claim> findById(String id);
    Mono<PageResult<Claim>> search(ClaimSearchCriteria criteria, int page, int size,
                                   ClaimSortField sortBy, boolean desc);
    Flux<Claim> findByPolicyId(long policyId);
}