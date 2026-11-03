package it.claimshub.claims_service.port;

import it.claimshub.claims_service.model.Claim;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClaimRepository {
    Mono<Claim> save(Claim claim);
    Mono<Claim> findById(String id);
    Flux<Claim> findAll();
}