package it.claimshub.claims_service.client;

import it.claimshub.claims_service.service.PolicyNotFoundException;
import it.claimshub.claims_service.service.PolicyServiceException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class PolicyClient {

    // solo i campi che ci servono: il resto del JSON viene ignorato
    public record PolicyInfo(Long id, String policyNumber, String status) {}

    private final WebClient webClient;

    public PolicyClient(WebClient policyWebClient) {
        this.webClient = policyWebClient;
    }

    public Mono<PolicyInfo> getPolicy(long id) {
        return webClient.get()
                .uri("/api/policies/{id}", id)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> Mono.error(new PolicyNotFoundException(id)))
                .onStatus(HttpStatusCode::isError,
                        response -> Mono.error(new PolicyServiceException(
                                "policy-service answered " + response.statusCode().value(), null)))
                .bodyToMono(PolicyInfo.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorMap(e -> e instanceof WebClientRequestException
                                 || e instanceof TimeoutException,
                        e -> new PolicyServiceException("policy-service unreachable", e));
    }
}