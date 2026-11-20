package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.model.*;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private Claim claim(String id, long policyId, String amount, ClaimStatus status, String createdAt) {
        return new Claim(id, policyId, "x", new BigDecimal(amount), status,
                Instant.parse(createdAt), "agent");
    }

    private ClaimSearchCriteria all() {
        return new ClaimSearchCriteria(null, null, null, null, null, null);
    }

    private void seed() {
        repo.save(claim("c1", 1, "100", ClaimStatus.OPEN, "2026-01-10T10:00:00Z")).block();
        repo.save(claim("c2", 1, "500", ClaimStatus.IN_REVIEW, "2026-02-10T10:00:00Z")).block();
        repo.save(claim("c3", 2, "900", ClaimStatus.APPROVED, "2026-03-10T10:00:00Z")).block();
        repo.save(claim("c4", 2, "1500", ClaimStatus.REJECTED, "2026-04-10T10:00:00Z")).block();
    }

    private List<String> ids(PageResult<Claim> page) {
        return page.content().stream().map(Claim::id).toList();
    }

    @Test
    void search_withoutFilters_returnsAll() {
        seed();
        StepVerifier.create(repo.search(all(), 0, 10, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> {
                    assertEquals(4, p.totalElements());
                    assertEquals(List.of("c1", "c2", "c3", "c4"), ids(p));
                })
                .verifyComplete();
    }

    @Test
    void search_byStatus() {
        seed();
        var criteria = new ClaimSearchCriteria(ClaimStatus.OPEN, null, null, null, null, null);
        StepVerifier.create(repo.search(criteria, 0, 10, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> assertEquals(List.of("c1"), ids(p)))
                .verifyComplete();
    }

    @Test
    void search_byPolicyId() {
        seed();
        var criteria = new ClaimSearchCriteria(null, 2L, null, null, null, null);
        StepVerifier.create(repo.search(criteria, 0, 10, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> assertEquals(List.of("c3", "c4"), ids(p)))
                .verifyComplete();
    }

    @Test
    void search_amountRange_isInclusive() {
        seed();
        var criteria = new ClaimSearchCriteria(null, null,
                new BigDecimal("500"), new BigDecimal("900"), null, null);
        StepVerifier.create(repo.search(criteria, 0, 10, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> assertEquals(List.of("c2", "c3"), ids(p)))
                .verifyComplete();
    }

    @Test
    void search_byCreatedDateRange() {
        seed();
        var criteria = new ClaimSearchCriteria(null, null, null, null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31));
        StepVerifier.create(repo.search(criteria, 0, 10, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> assertEquals(List.of("c2", "c3"), ids(p)))
                .verifyComplete();
    }

    @Test
    void search_sortByAmountDescending() {
        seed();
        StepVerifier.create(repo.search(all(), 0, 10, ClaimSortField.AMOUNT, true))
                .assertNext(p -> assertEquals(List.of("c4", "c3", "c2", "c1"), ids(p)))
                .verifyComplete();
    }

    @Test
    void search_pagination_returnsSliceAndFullTotal() {
        seed();
        StepVerifier.create(repo.search(all(), 1, 3, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> {
                    assertEquals(List.of("c4"), ids(p));
                    assertEquals(4, p.totalElements());
                })
                .verifyComplete();
    }

    @Test
    void search_pageBeyondTheEnd_isEmptyButKeepsTotal() {
        seed();
        StepVerifier.create(repo.search(all(), 5, 10, ClaimSortField.CREATED_AT, false))
                .assertNext(p -> {
                    assertTrue(p.content().isEmpty());
                    assertEquals(4, p.totalElements());
                })
                .verifyComplete();
    }
}