package it.claimshub.claims_service.client;

import it.claimshub.claims_service.model.HeaderNames;
import it.claimshub.claims_service.service.PolicyNotFoundException;
import it.claimshub.claims_service.service.PolicyServiceException;
import it.claimshub.claims_service.web.RequestIdWebFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PolicyClientTest {

    private static final String POLICY_JSON = """
            {"id":1,"policyNumber":"POL-0001","holderName":"Mario Rossi",
             "type":"AUTO","status":"ACTIVE","premium":450.00}""";

    MockWebServer server;
    PolicyClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PolicyClient(WebClient.builder()
                .baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void getPolicy_returnsPolicy_andIgnoresExtraFields() {
        server.enqueue(json(200, POLICY_JSON));

        StepVerifier.create(client.getPolicy(1L))
                .assertNext(p -> {
                    assertEquals("POL-0001", p.policyNumber());
                    assertEquals("ACTIVE", p.status());
                })
                .verifyComplete();
    }

    @Test
    void getPolicy_404_becomesPolicyNotFound() {
        server.enqueue(json(404, "{}"));

        StepVerifier.create(client.getPolicy(999L))
                .expectError(PolicyNotFoundException.class)
                .verify();
    }

    @Test
    void getPolicy_500_becomesPolicyServiceException() {
        server.enqueue(json(500, "{}"));

        StepVerifier.create(client.getPolicy(1L))
                .expectError(PolicyServiceException.class)
                .verify();
    }

    @Test
    void getPolicy_401_becomesPolicyServiceException() {
        server.enqueue(json(401, "{}"));

        StepVerifier.create(client.getPolicy(1L))
                .expectError(PolicyServiceException.class)
                .verify();
    }

    @Test
    void getPolicy_slowServer_timesOut_asPolicyServiceException() {
        server.enqueue(json(200, POLICY_JSON).setBodyDelay(6, TimeUnit.SECONDS));

        StepVerifier.create(client.getPolicy(1L))
                .expectError(PolicyServiceException.class)
                .verify(Duration.ofSeconds(8));
    }

    @Test
    void getPolicy_serverDown_becomesPolicyServiceException() {
        // porta 1: nessuno è in ascolto, la connessione viene rifiutata subito
        var unreachable = new PolicyClient(WebClient.builder().baseUrl("http://localhost:1").build());

        StepVerifier.create(unreachable.getPolicy(1L))
                .expectError(PolicyServiceException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void webClientConfig_propagatesTokenRequestIdAndClientApp() throws Exception {
        server.enqueue(json(200, POLICY_JSON));
        var propagating = new PolicyClient(
                new WebClientConfig().policyWebClient(server.url("/").toString()));

        Jwt jwt = Jwt.withTokenValue("token-di-prova")
                .header("alg", "HS256").subject("agent").build();
        var auth = new JwtAuthenticationToken(jwt);

        StepVerifier.create(propagating.getPolicy(1L)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                        .contextWrite(ctx -> ctx.put(RequestIdWebFilter.CTX_KEY, "req-12345678")))
                .expectNextCount(1)
                .verifyComplete();

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/policies/1", request.getPath());
        assertEquals("Bearer token-di-prova", request.getHeader("Authorization"));
        assertEquals("req-12345678", request.getHeader(HeaderNames.REQUEST_ID));
        assertEquals("claims-service", request.getHeader(HeaderNames.CLIENT_APP));
    }
}