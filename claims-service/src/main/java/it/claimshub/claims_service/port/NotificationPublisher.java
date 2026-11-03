package it.claimshub.claims_service.port;

import reactor.core.publisher.Mono;

public interface NotificationPublisher {
    Mono<Void> publish(String type, String claimId, String message);
}