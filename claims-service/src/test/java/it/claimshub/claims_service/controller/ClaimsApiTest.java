package it.claimshub.claims_service.controller;

import it.claimshub.claims_service.client.PolicyClient;
import it.claimshub.claims_service.service.PolicyNotFoundException;
import it.claimshub.claims_service.service.PolicyServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
class ClaimsApiTest {

    @Autowired
    ApplicationContext context;

    @MockitoBean
    PolicyClient policyClient;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
        when(policyClient.getPolicy(anyLong()))
                .thenReturn(Mono.just(new PolicyClient.PolicyInfo(1L, "POL-0001", "ACTIVE")));
    }

    // ---------- utenti simulati ----------

    private WebTestClient asAgent() {
        return client.mutateWith(mockJwt()
                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))
                .jwt(j -> j.subject("agent").claim("roles", List.of("AGENT"))));
    }

    private WebTestClient asAdmin() {
        return client.mutateWith(mockJwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(j -> j.subject("admin").claim("roles", List.of("ADMIN"))));
    }

    // ---------- helper ----------

    private static final String NEW_CLAIM =
            "{\"policyId\":1,\"description\":\"Tamponamento\",\"amount\":1500.00}";

    private String createClaim() {
        Map<?, ?> body = asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(NEW_CLAIM)
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("id");
    }

    private void changeStatus(WebTestClient who, String id, String status, int expected) {
        who.patch().uri("/api/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"" + status + "\"}")
                .exchange().expectStatus().isEqualTo(expected);
    }

    private BodyInserters.MultipartInserter pdf(String name, byte[] content, MediaType type) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return name; }
        }).contentType(type);
        return BodyInserters.fromMultipartData(builder.build());
    }

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
    void changeStatus_asAgent_is403_withJsonBody() {
        String id = createClaim();
        asAgent().patch().uri("/api/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"IN_REVIEW\"}")
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.message").isEqualTo("Access denied");
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

    // ---------- workflow e storico ----------

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

    // ---------- documenti ----------

    @Test
    void documents_uploadListDownload_roundTrip() {
        String id = createClaim();
        byte[] content = "contenuto finto di un pdf".getBytes(StandardCharsets.UTF_8);

        asAgent().post().uri("/api/claims/{id}/documents", id)
                .body(pdf("prova.pdf", content, MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.fileName").isEqualTo("prova.pdf")
                .jsonPath("$.uploadedBy").isEqualTo("agent");

        String docId = (String) asAgent().get().uri("/api/claims/{id}/documents", id)
                .exchange().expectStatus().isOk()
                .expectBody(List.class).returnResult().getResponseBody().stream()
                .map(o -> ((Map<?, ?>) o).get("id")).findFirst().orElseThrow();

        asAgent().get().uri("/api/claims/{id}/documents/{d}", id, docId).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().value("Content-Disposition", h -> assertTrue(h.startsWith("attachment")))
                .expectBody(byte[].class).isEqualTo(content);
    }

    @Test
    void documents_unsupportedType_is400() {
        String id = createClaim();
        asAgent().post().uri("/api/claims/{id}/documents", id)
                .body(pdf("note.txt", "testo".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void documents_forMissingClaim_is404() {
        asAgent().post().uri("/api/claims/non-esiste/documents")
                .body(pdf("a.pdf", "x".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void documents_tooLarge_is413() {
        String id = createClaim();
        asAgent().post().uri("/api/claims/{id}/documents", id)
                .body(pdf("grande.pdf", new byte[6 * 1024 * 1024], MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isEqualTo(413);
    }

    @Test
    void documents_cannotBeDownloadedThroughAnotherClaim() {
        String first = createClaim();
        String second = createClaim();

        asAgent().post().uri("/api/claims/{id}/documents", first)
                .body(pdf("a.pdf", "x".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isCreated();
        String docId = (String) asAgent().get().uri("/api/claims/{id}/documents", first)
                .exchange().expectBody(List.class).returnResult().getResponseBody().stream()
                .map(o -> ((Map<?, ?>) o).get("id")).findFirst().orElseThrow();

        asAgent().get().uri("/api/claims/{id}/documents/{d}", second, docId).exchange()
                .expectStatus().isNotFound();
    }

    // ---------- request id ----------

    @Test
    void requestId_isGenerated_echoed_andReplacedWhenMalicious() {
        client.get().uri("/actuator/health").exchange()
                .expectHeader().exists("X-Request-Id");

        client.get().uri("/actuator/health").header("X-Request-Id", "prova-12345678").exchange()
                .expectHeader().valueEquals("X-Request-Id", "prova-12345678");

        client.get().uri("/actuator/health").header("X-Request-Id", "bad value forged")
                .exchange()
                .expectHeader().value("X-Request-Id", v -> assertNotEquals("bad value forged", v));
    }

    @Test
    void requestId_isPresentAlsoOn401() {
        client.get().uri("/api/claims").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id");
    }

    private String createClaim(long policyId, String amount) {
        Map<?, ?> body = asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":" + policyId + ",\"description\":\"Test\",\"amount\":" + amount + "}")
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("id");
    }

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
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        asAgent().get().uri("/api/claims?policyId=7007&createdFrom=" + today).exchange()
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