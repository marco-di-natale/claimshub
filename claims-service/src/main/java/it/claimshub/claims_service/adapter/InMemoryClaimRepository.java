package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimSearchCriteria;
import it.claimshub.claims_service.model.ClaimSortField;
import it.claimshub.claims_service.model.PageResult;
import it.claimshub.claims_service.port.ClaimRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryClaimRepository implements ClaimRepository {

    private final Map<String, Claim> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Claim> save(Claim claim) {
        return Mono.fromSupplier(() -> {
            store.put(claim.id(), claim);
            return claim;
        });
    }

    @Override
    public Mono<Claim> findById(String id) {
        return Mono.fromSupplier(() -> store.get(id));   // null = Mono vuoto
    }

    @Override
    public Mono<PageResult<Claim>> search(ClaimSearchCriteria criteria, int page, int size,
                                          ClaimSortField sortBy, boolean desc) {
        return Mono.fromSupplier(() -> {
            Comparator<Claim> order = switch (sortBy) {
                case CREATED_AT -> Comparator.comparing(Claim::createdAt);
                case AMOUNT -> Comparator.comparing(Claim::amount);
            };
            // a parità di valore decide l'id, così l'ordine è stabile tra una pagina e l'altra
            order = order.thenComparing(Claim::id);
            if (desc) {
                order = order.reversed();
            }
            List<Claim> matching = store.values().stream()
                    .filter(criteria::matches)
                    .sorted(order)
                    .toList();
            List<Claim> content = matching.stream()
                    .skip((long) page * size)
                    .limit(size)
                    .toList();
            return new PageResult<>(content, page, size, matching.size());
        });
    }

    @Override
    public Flux<Claim> findByPolicyId(long policyId) {
        return Flux.defer(() -> Flux.fromIterable(store.values().stream()
                .filter(c -> c.policyId() == policyId)
                .toList()));
    }
}