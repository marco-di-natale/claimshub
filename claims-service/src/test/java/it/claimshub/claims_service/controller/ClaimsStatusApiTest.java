package it.claimshub.claims_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class ClaimsStatusApiTest extends AbstractClaimsApiTest {

    @Test
    void workflow_validPath_thenConflicts_andHistoryHasAllEvents() {
        String id = createClaim();

        changeStatus(asAdmin(), id, "APPROVED", 409);   // OPEN -> APPROVED non ammesso
        changeStatus(asAdmin(), id, "IN_REVIEW", 200);
        changeStatus(asAdmin(), id, "APPROVED", 200);
        changeStatus(asAdmin(), id, "REJECTED", 409);   // APPROVED è finale

        asAgent().get().uri("/api/claims/{id}/history", id).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].type").isEqualTo("CREATED")
                .jsonPath("$[1].type").isEqualTo("STATUS_CHANGED")
                .jsonPath("$[2].actor").isEqualTo("admin");
    }

    @Test
    void changeStatus_withUnknownStatus_is400() {
        String id = createClaim();
        asAdmin().patch().uri("/api/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"BOH\"}")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void history_ofMissingClaim_is404() {
        asAgent().get().uri("/api/claims/non-esiste/history").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void changeStatus_asAgent_is403_withJsonBody() {
        String id = createClaim();
        asAgent().patch().uri("/api/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"IN_REVIEW\"}")
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.message").isEqualTo("Access denied");
    }

    @Test
    void changeStatus_onMissingClaim_is404() {
        asAdmin().patch().uri("/api/claims/{id}/status", "non-esiste")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"IN_REVIEW\"}")
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void changeStatus_toSameStatus_is409() {
        String id = createClaim();                       // nasce OPEN
        changeStatus(asAdmin(), id, "OPEN", 409);        // OPEN -> OPEN non è una transizione
    }
}
