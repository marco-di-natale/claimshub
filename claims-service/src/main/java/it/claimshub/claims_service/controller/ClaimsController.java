package it.claimshub.claims_service.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimsController {

    public record WhoAmI(String username, List<String> roles) {}

    @GetMapping("/whoami")
    public Mono<WhoAmI> whoami(@AuthenticationPrincipal Jwt jwt) {
        return Mono.just(new WhoAmI(jwt.getSubject(), jwt.getClaimAsStringList("roles")));
    }
}