package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimStatus;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryClaimRepositoryTest {

    InMemoryClaimRepository repo = new InMemoryClaimRepository();
    Claim claim = new Claim("c1", 1L, "x", BigDecimal.TEN,
            ClaimStatus.OPEN, Instant.now(), "agent");

    @Test
    void save_doesNothing_untilSubscribed() {
        repo.save(claim);   // il Mono non viene eseguito

        StepVerifier.create(repo.findById("c1")).verifyComplete();   // nessun valore
    }

    @Test
    void save_thenFind_returnsClaim() {
        StepVerifier.create(repo.save(claim).then(repo.findById("c1")))
                .assertNext(c -> assertEquals("c1", c.id()))
                .verifyComplete();
    }
}