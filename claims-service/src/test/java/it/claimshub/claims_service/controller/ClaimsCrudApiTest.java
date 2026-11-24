package it.claimshub.claims_service.controller;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.model.HeaderNames;
import it.claimshub.claims_service.service.PolicyNotFoundException;
import it.claimshub.claims_service.service.PolicyServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ClaimsCrudApiTest extends AbstractClaimsApiTest {

    // ---------- 401 / 403 ----------

    @Test
    void health_isPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void claims_withoutToken_is401_withJsonBody() {
        client.get().uri("/api/claims").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void claims_withFakeToken_is401() {
        client.get().uri("/api/claims").header("Authorization", "Bearer abc.def.ghi")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unknownPath_is403_becauseOfDenyAll() {
        asAgent().get().uri("/api/qualcosa").exchange().expectStatus().isForbidden();
    }

    // ---------- percorsi felici ----------

    @Test
    void whoami_returnsUserAndRoles() {
        asAgent().get().uri("/api/claims/whoami").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("agent")
                .jsonPath("$.roles[0]").isEqualTo("AGENT");
    }

    @Test
    void create_asAgent_is201_openStatus_andAuthorFromToken() {
        asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(NEW_CLAIM)
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("OPEN")
                .jsonPath("$.createdBy").isEqualTo("agent")
                .jsonPath("$.id").isNotEmpty();
    }

    @Test
    void list_andGet_returnTheCreatedClaim() {
        String id = createClaim();

        asAgent().get().uri("/api/claims").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.content").isArray();
        asAgent().get().uri("/api/claims/{id}", id).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(id);
    }

    // ---------- errori di dominio ----------

    @Test
    void get_unknownClaim_is404_withJsonBody() {
        asAgent().get().uri("/api/claims/non-esiste").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void create_invalidBody_is400_namingTheWrongFields() {
        asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":1,\"description\":\"\",\"amount\":-5}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").value(m -> {
                    assertTrue(m.toString().contains("description"));
                    assertTrue(m.toString().contains("amount"));
                });
    }

    @Test
    void create_withNonActivePolicy_is422() {
        when(policyClient.getPolicy(3L))
                .thenReturn(Mono.just(new PolicyClient.PolicyInfo(3L, "POL-0003", "EXPIRED")));

        asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":3,\"description\":\"Furto\",\"amount\":800}")
                .exchange().expectStatus().isEqualTo(422);
    }

    @Test
    void create_withUnknownPolicy_is422() {
        when(policyClient.getPolicy(999L)).thenReturn(Mono.error(new PolicyNotFoundException(999L)));

        asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":999,\"description\":\"Boh\",\"amount\":100}")
                .exchange().expectStatus().isEqualTo(422);
    }

    @Test
    void create_whenPolicyServiceIsDown_is502() {
        when(policyClient.getPolicy(anyLong()))
                .thenReturn(Mono.error(new PolicyServiceException("down", null)));

        asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(NEW_CLAIM)
                .exchange().expectStatus().isEqualTo(502);
    }

    // ---------- request id ----------

    @Test
    void requestId_isGenerated_echoed_andReplacedWhenMalicious() {
        client.get().uri("/actuator/health").exchange()
                .expectHeader().exists(HeaderNames.REQUEST_ID);

        client.get().uri("/actuator/health").header(HeaderNames.REQUEST_ID, "prova-12345678").exchange()
                .expectHeader().valueEquals(HeaderNames.REQUEST_ID, "prova-12345678");

        client.get().uri("/actuator/health").header(HeaderNames.REQUEST_ID, "bad value forged")
                .exchange()
                .expectHeader().value(HeaderNames.REQUEST_ID, v -> assertNotEquals("bad value forged", v));
    }

    @Test
    void requestId_isPresentAlsoOn401() {
        client.get().uri("/api/claims").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(HeaderNames.REQUEST_ID);
    }

}