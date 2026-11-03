package it.claimshub.claims_service.port;

import it.claimshub.claims_service.model.ClaimEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClaimHistoryStore {
    Mono<Void> append(ClaimEvent event);
    Flux<ClaimEvent> findByClaimId(String claimId);
}