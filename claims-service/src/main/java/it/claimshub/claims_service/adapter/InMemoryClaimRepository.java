package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.port.ClaimRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryClaimRepository implements ClaimRepository {

    private final Map<String, Claim> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Claim> save(Claim claim) {
        store.put(claim.id(), claim);
        return Mono.just(claim);
    }

    @Override
    public Mono<Claim> findById(String id) {
        return Mono.justOrEmpty(store.get(id));
    }

    @Override
    public Flux<Claim> findAll() {
        return Flux.fromIterable(store.values())
                   .sort(Comparator.comparing(Claim::createdAt));
    }
}