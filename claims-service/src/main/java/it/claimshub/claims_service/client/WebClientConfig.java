package it.claimshub.claims_service.client;

import it.claimshub.claims_service.web.RequestIdWebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient policyWebClient(@Value("${app.policy-service.url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(propagationFilter())
                .build();
    }

    // eseguito per OGNI richiesta in uscita: aggiunge token, request id e nome app
    private ExchangeFilterFunction propagationFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            String requestId = ctx.getOrDefault(RequestIdWebFilter.CTX_KEY, (String) null);
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .ofType(JwtAuthenticationToken.class)
                    .map(auth -> Optional.of(auth.getToken().getTokenValue()))
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(token -> {
                        ClientRequest.Builder builder = ClientRequest.from(request)
                                .header("X-Client-App", "claims-service");
                        token.ifPresent(t -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + t));
                        if (requestId != null) {
                            builder.header(RequestIdWebFilter.HEADER, requestId);
                        }
                        return next.exchange(builder.build());
                    });
        });
    }
}