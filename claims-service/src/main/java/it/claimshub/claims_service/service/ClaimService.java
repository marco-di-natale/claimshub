package it.claimshub.claims_service.service;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimEvent;
import it.claimshub.claims_service.model.ClaimStatus;
import it.claimshub.claims_service.model.CreateClaimRequest;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import it.claimshub.claims_service.port.ClaimRepository;
import it.claimshub.claims_service.port.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimService {

    // stato attuale -> stati in cui può andare
    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            ClaimStatus.OPEN, Set.of(ClaimStatus.IN_REVIEW),
            ClaimStatus.IN_REVIEW, Set.of(ClaimStatus.APPROVED, ClaimStatus.REJECTED),
            ClaimStatus.APPROVED, Set.of(),
            ClaimStatus.REJECTED, Set.of());

    private final ClaimRepository repository;
    private final ClaimHistoryStore history;
    private final NotificationPublisher notifier;

    public Mono<Claim> create(CreateClaimRequest request, String username) {
        Claim claim = new Claim(UUID.randomUUID().toString(), request.policyId(),
                request.description(), request.amount(), ClaimStatus.OPEN,
                Instant.now(), username);
        return repository.save(claim)
                .flatMap(saved -> record(saved, "CREATED", "Claim opened", username)
                        .thenReturn(saved));
    }

    public Mono<Claim> findById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ClaimNotFoundException(id)));
    }

    public Flux<Claim> findAll() {
        return repository.findAll();
    }

    public Mono<Claim> changeStatus(String id, ClaimStatus newStatus, String username) {
        return findById(id).flatMap(current -> {
            if (!TRANSITIONS.get(current.status()).contains(newStatus)) {
                return Mono.error(new InvalidStatusTransitionException(current.status(), newStatus));
            }
            Claim updated = new Claim(current.id(), current.policyId(), current.description(),
                    current.amount(), newStatus, current.createdAt(), current.createdBy());
            return repository.save(updated)
                    .flatMap(saved -> record(saved, "STATUS_CHANGED",
                            current.status() + " -> " + newStatus, username)
                            .thenReturn(saved));
        });
    }

    public Flux<ClaimEvent> history(String id) {
        return findById(id).thenMany(history.findByClaimId(id));
    }

    // scrive lo storico e manda la notifica, poi restituisce "fatto"
    private Mono<Void> record(Claim claim, String type, String detail, String actor) {
        ClaimEvent event = new ClaimEvent(claim.id(), Instant.now(), type, detail, actor);
        return history.append(event)
                .then(notifier.publish(type, claim.id(), detail));
    }
}