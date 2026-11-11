package it.claimshub.claims_service.service;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimEvent;
import it.claimshub.claims_service.model.ClaimStatus;
import it.claimshub.claims_service.model.CreateClaimRequest;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import it.claimshub.claims_service.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    ClaimRepository repository;
    @Mock
    ClaimHistoryStore history;
    @Mock
    ClaimAudit audit;
    @Mock
    PolicyClient policyClient;

    @InjectMocks
    ClaimService service;

    private Claim claim(String id, ClaimStatus status) {
        return new Claim(id, 1L, "Tamponamento", new BigDecimal("100.00"),
                status, Instant.now(), "agent");
    }

    private CreateClaimRequest request() {
        return new CreateClaimRequest(1L, "Tamponamento", new BigDecimal("1500.00"));
    }

    // storico e notifica "vanno a buon fine"
    private void stubSideEffects() {
        lenient().when(history.append(any())).thenReturn(Mono.empty());
        lenient().when(audit.record(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    // ---------- create ----------

    @Test
    void create_withActivePolicy_savesOpenClaim_andNotifies() {
        when(policyClient.getPolicy(1L))
                .thenReturn(Mono.just(new PolicyClient.PolicyInfo(1L, "POL-0001", "ACTIVE")));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        stubSideEffects();

        StepVerifier.create(service.create(request(), "agent"))
                .assertNext(c -> {
                    assertNotNull(c.id());
                    assertEquals(ClaimStatus.OPEN, c.status());
                    assertEquals("agent", c.createdBy());
                })
                .verifyComplete();

        verify(audit).record(any(), eq("CREATED"), any(), eq("agent"));
    }

    @Test
    void create_withNonActivePolicy_failsAndSavesNothing() {
        when(policyClient.getPolicy(1L))
                .thenReturn(Mono.just(new PolicyClient.PolicyInfo(1L, "POL-0003", "EXPIRED")));

        StepVerifier.create(service.create(request(), "agent"))
                .expectError(PolicyNotActiveException.class)
                .verify();

        verifyNoInteractions(repository, history, audit);
    }

    @Test
    void create_withUnknownPolicy_failsAndSavesNothing() {
        when(policyClient.getPolicy(1L))
                .thenReturn(Mono.error(new PolicyNotFoundException(1L)));

        StepVerifier.create(service.create(request(), "agent"))
                .expectError(PolicyNotFoundException.class)
                .verify();

        verifyNoInteractions(repository, history, audit);
    }

    // ---------- findById ----------

    @Test
    void findById_returnsClaim_whenPresent() {
        when(repository.findById("c1")).thenReturn(Mono.just(claim("c1", ClaimStatus.OPEN)));

        StepVerifier.create(service.findById("c1"))
                .assertNext(c -> assertEquals("c1", c.id()))
                .verifyComplete();
    }

    @Test
    void findById_fails_whenMissing() {
        when(repository.findById("x")).thenReturn(Mono.empty());

        StepVerifier.create(service.findById("x"))
                .expectError(ClaimNotFoundException.class)
                .verify();
    }

    // ---------- macchina a stati ----------

    @ParameterizedTest
    @CsvSource({
            "OPEN,IN_REVIEW,true",
            "OPEN,APPROVED,false",
            "OPEN,REJECTED,false",
            "IN_REVIEW,APPROVED,true",
            "IN_REVIEW,REJECTED,true",
            "IN_REVIEW,OPEN,false",
            "APPROVED,REJECTED,false",
            "REJECTED,OPEN,false"})
    void changeStatus_followsStateMachine(ClaimStatus from, ClaimStatus to, boolean allowed) {
        when(repository.findById("c1")).thenReturn(Mono.just(claim("c1", from)));
        lenient().when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        stubSideEffects();

        var result = service.changeStatus("c1", to, "admin");

        if (allowed) {
            StepVerifier.create(result)
                    .assertNext(c -> assertEquals(to, c.status()))
                    .verifyComplete();
            verify(audit).record(eq("c1"), eq("STATUS_CHANGED"), any(), eq("admin"));
        } else {
            StepVerifier.create(result)
                    .expectError(InvalidStatusTransitionException.class)
                    .verify();
            verify(repository, never()).save(any());
            verifyNoInteractions(history, audit);
        }
    }

    @Test
    void changeStatus_onMissingClaim_fails() {
        when(repository.findById("x")).thenReturn(Mono.empty());

        StepVerifier.create(service.changeStatus("x", ClaimStatus.IN_REVIEW, "admin"))
                .expectError(ClaimNotFoundException.class)
                .verify();
    }

    // ---------- storico ----------

    @Test
    void history_returnsEvents_forExistingClaim() {
        var event = new ClaimEvent("c1", Instant.now(), "CREATED", "Claim opened", "agent");
        when(repository.findById("c1")).thenReturn(Mono.just(claim("c1", ClaimStatus.OPEN)));
        when(history.findByClaimId("c1")).thenReturn(Flux.just(event));

        StepVerifier.create(service.history("c1"))
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    void history_failsWith404Error_forMissingClaim_insteadOfEmptyList() {
        when(repository.findById("x")).thenReturn(Mono.empty());
        lenient().when(history.findByClaimId("x")).thenReturn(Flux.empty());

        StepVerifier.create(service.history("x"))
                .expectError(ClaimNotFoundException.class)
                .verify();
    }
}