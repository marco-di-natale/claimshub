package it.claimshub.claims_service.controller;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClaimsSearchApiTest extends AbstractClaimsApiTest {

    @Test
    void search_byPolicyId_returnsOnlyThatPolicy() {
        createClaim(7001, "100");
        createClaim(7001, "200");
        createClaim(7002, "300");

        asAgent().get().uri("/api/claims?policyId=7001").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void search_byAmountRange() {
        createClaim(7003, "100");
        createClaim(7003, "500");
        createClaim(7003, "1500");

        asAgent().get().uri("/api/claims?policyId=7003&minAmount=200&maxAmount=1000").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void search_byStatus() {
        createClaim(7004, "100");
        String second = createClaim(7004, "200");
        changeStatus(asAdmin(), second, "IN_REVIEW", 200);

        asAgent().get().uri("/api/claims?policyId=7004&status=IN_REVIEW").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(second);
    }

    @Test
    void search_pagination() {
        for (int i = 1; i <= 5; i++) {
            createClaim(7005, String.valueOf(i * 100));
        }

        asAgent().get().uri("/api/claims?policyId=7005&size=2&page=2").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(5)
                .jsonPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void search_sortByAmountAscending() {
        createClaim(7006, "300");
        createClaim(7006, "100");
        createClaim(7006, "200");

        asAgent().get().uri("/api/claims?policyId=7006&sortBy=AMOUNT&desc=false").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.content[0].amount").value(a ->
                        assertEquals(0, new BigDecimal(a.toString()).compareTo(new BigDecimal("100"))));
    }

    @Test
    void search_byCreatedDate_usesIsoDates() {
        createClaim(7007, "100");
        // finestra larga un giorno per lato: vale anche se il test gira a cavallo della mezzanotte
        String from = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        String to = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

        asAgent().get().uri("/api/claims?policyId=7007&createdFrom=" + from + "&createdTo=" + to).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1);
        asAgent().get().uri("/api/claims?policyId=7007&createdTo=2000-01-01").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void search_invalidPaging_is400() {
        asAgent().get().uri("/api/claims?size=0").exchange().expectStatus().isBadRequest();
        asAgent().get().uri("/api/claims?size=101").exchange().expectStatus().isBadRequest();
    }

    @Test
    void search_minGreaterThanMax_is400() {
        asAgent().get().uri("/api/claims?minAmount=900&maxAmount=100").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void search_unknownStatusOrSortField_is400() {
        asAgent().get().uri("/api/claims?status=BOH").exchange().expectStatus().isBadRequest();
        asAgent().get().uri("/api/claims?sortBy=description").exchange().expectStatus().isBadRequest();
    }
}
