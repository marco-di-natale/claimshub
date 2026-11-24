package it.claimshub.claims_service.controller;

import it.claimshub.claims_service.client.PolicyClient;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractClaimsApiTest {

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

    protected WebTestClient asAgent() {
        return client.mutateWith(mockJwt()
                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))
                .jwt(j -> j.subject("agent").claim("roles", List.of("AGENT"))));
    }

    protected WebTestClient asAdmin() {
        return client.mutateWith(mockJwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(j -> j.subject("admin").claim("roles", List.of("ADMIN"))));
    }

    // ---------- helper ----------

    protected static final String NEW_CLAIM =
            "{\"policyId\":1,\"description\":\"Tamponamento\",\"amount\":1500.00}";

    protected String createClaim() {
        Map<?, ?> body = asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(NEW_CLAIM)
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("id");
    }

    protected void changeStatus(WebTestClient who, String id, String status, int expected) {
        who.patch().uri("/api/claims/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"" + status + "\"}")
                .exchange().expectStatus().isEqualTo(expected);
    }

    protected BodyInserters.MultipartInserter pdf(String name, byte[] content, MediaType type) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return name; }
        }).contentType(type);
        return BodyInserters.fromMultipartData(builder.build());
    }

    protected String createClaim(long policyId, String amount) {
        Map<?, ?> body = asAgent().post().uri("/api/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":" + policyId + ",\"description\":\"Test\",\"amount\":" + amount + "}")
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("id");
    }

}
