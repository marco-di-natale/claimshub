package it.claimshub.claims_service.service;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimStatus;
import it.claimshub.claims_service.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicySummaryServiceTest {

    @Mock
    ClaimRepository repository;
    @Mock
    PolicyClient policyClient;
    @InjectMocks
    ClaimService service;

    private Claim claim(String id, String amount, ClaimStatus status) {
        return new Claim(id, 5L, "x", new BigDecimal(amount), status,
                Instant.parse("2026-01-01T00:00:00Z"), "agent");
    }

    private void claimsAre(Claim... claims) {
        when(repository.findByPolicyId(anyLong())).thenReturn(Flux.fromArray(claims));
    }

    @Test
    void summary_combinesPolicyAndClaimStats() {
        when(policyClient.getPolicy(5L))
                .thenReturn(Mono.just(new PolicyClient.PolicyInfo(5L, "POL-5", "ACTIVE")));
        claimsAre(claim("c1", "100", ClaimStatus.OPEN),
                  claim("c2", "300", ClaimStatus.OPEN),
                  claim("c3", "700", ClaimStatus.APPROVED));

        StepVerifier.create(service.policySummary(5L))
                .assertNext(s -> {
                    assertEquals("POL-5", s.policy().policyNumber());
                    assertEquals(3, s.claims().total());
                    assertEquals(0, new BigDecimal("1100").compareTo(s.claims().totalAmount()));
                    assertEquals(2L, s.claims().byStatus().get(ClaimStatus.OPEN));
                    assertEquals(1L, s.claims().byStatus().get(ClaimStatus.APPROVED));
                    assertEquals(0L, s.claims().byStatus().get(ClaimStatus.REJECTED));
                })
                .verifyComplete();
    }

    @Test
    void summary_withNoClaims_hasZeroStats() {
        when(policyClient.getPolicy(5L))
                .thenReturn(Mono.just(new PolicyClient.PolicyInfo(5L, "POL-5", "ACTIVE")));
        claimsAre();

        StepVerifier.create(service.policySummary(5L))
                .assertNext(s -> {
                    assertEquals(0, s.claims().total());
                    assertEquals(0, BigDecimal.ZERO.compareTo(s.claims().totalAmount()));
                })
                .verifyComplete();
    }

    @Test
    void summary_unknownPolicy_propagatesNotFound() {
        when(policyClient.getPolicy(9L)).thenReturn(Mono.error(new PolicyNotFoundException(9L)));
        claimsAre();

        StepVerifier.create(service.policySummary(9L))
                .expectError(PolicyNotFoundException.class)
                .verify();
    }

    @Test
    void summary_policyServiceDown_propagatesBadGateway() {
        when(policyClient.getPolicy(5L))
                .thenReturn(Mono.error(new PolicyServiceException("policy-service unreachable", null)));
        claimsAre();

        StepVerifier.create(service.policySummary(5L))
                .expectError(PolicyServiceException.class)
                .verify();
    }
}