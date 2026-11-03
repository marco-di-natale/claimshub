package it.claimshub.claims_service.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String CTX_KEY = "requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        // stesso criterio di policy-service: formato sicuro, altrimenti ne genero uno
        String id = (incoming != null && VALID.matcher(incoming).matches())
                ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, id);
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(CTX_KEY, id));
    }
}