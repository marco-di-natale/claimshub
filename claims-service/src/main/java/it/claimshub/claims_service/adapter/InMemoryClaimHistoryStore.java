package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.model.ClaimEvent;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryClaimHistoryStore implements ClaimHistoryStore {

    private final Map<String, List<ClaimEvent>> events = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> append(ClaimEvent event) {
        events.computeIfAbsent(event.claimId(), k -> new CopyOnWriteArrayList<>()).add(event);
        return Mono.empty();
    }

    @Override
    public Flux<ClaimEvent> findByClaimId(String claimId) {
        return Flux.fromIterable(events.getOrDefault(claimId, List.of()));
    }
}