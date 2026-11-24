package it.claimshub.claims_service.service;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimStatus;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import it.claimshub.claims_service.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimQueryServiceTest {

    @Mock
    ClaimRepository repository;
    @Mock
    ClaimHistoryStore history;
    @Mock
    PolicyClient policyClient;
    @InjectMocks
    ClaimQueryService service;

    private Claim claim() {
        return new Claim("c1", 1L, "x", new BigDecimal("100"), ClaimStatus.OPEN,
                Instant.parse("2026-01-01T00:00:00Z"), "agent");
    }

    @Test
    void findById_returnsClaim_whenPresent() {
        Claim claim = claim();
        when(repository.findById("c1")).thenReturn(Mono.just(claim));

        StepVerifier.create(service.findById("c1"))
                .expectNext(claim)
                .verifyComplete();
    }

    @Test
    void findById_missing_failsWithNotFound() {
        when(repository.findById("x")).thenReturn(Mono.empty());

        StepVerifier.create(service.findById("x"))
                .expectError(ClaimNotFoundException.class)
                .verify();
    }

    @Test
    void history_ofMissingClaim_failsInsteadOfReturningEmptyList() {
        when(repository.findById("x")).thenReturn(Mono.empty());

        StepVerifier.create(service.history("x"))
                .expectError(ClaimNotFoundException.class)
                .verify();
        verifyNoInteractions(history);   // grazie a Flux.defer, lo storico non viene nemmeno letto
    }
}