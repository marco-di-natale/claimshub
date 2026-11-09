package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.port.NotificationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LoggingNotificationPublisher implements NotificationPublisher {

    @Override
    public Mono<Void> publish(String type, String claimId, String message) {
        return Mono.fromRunnable(() ->
                log.info("[SQS-simulato] type={} claimId={} message={}", type, claimId, message));
    }
}