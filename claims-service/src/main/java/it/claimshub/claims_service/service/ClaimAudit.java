package it.claimshub.claims_service.service;

import it.claimshub.claims_service.model.ClaimEvent;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import it.claimshub.claims_service.port.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ClaimAudit {

    private final ClaimHistoryStore history;
    private final NotificationPublisher notifier;

    public Mono<Void> record(String claimId, String type, String detail, String actor) {
        ClaimEvent event = new ClaimEvent(claimId, Instant.now(), type, detail, actor);
        return history.append(event).then(notifier.publish(type, claimId, detail));
    }
}