package it.claimshub.claims_service.controller;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimEvent;
import it.claimshub.claims_service.model.CreateClaimRequest;
import it.claimshub.claims_service.model.UpdateStatusRequest;
import it.claimshub.claims_service.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimsController {

    public record WhoAmI(String username, List<String> roles) {
    }

    private final ClaimService service;

    @GetMapping("/whoami")
    public Mono<WhoAmI> whoami(@AuthenticationPrincipal Jwt jwt) {
        return Mono.just(new WhoAmI(jwt.getSubject(), jwt.getClaimAsStringList("roles")));
    }

    @GetMapping
    public Flux<Claim> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Claim> get(@PathVariable String id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/history")
    public Flux<ClaimEvent> history(@PathVariable String id) {
        return service.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Claim> create(@Valid @RequestBody CreateClaimRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, jwt.getSubject());
    }

    @PatchMapping("/{id}/status")
    public Mono<Claim> changeStatus(@PathVariable String id,
                                    @Valid @RequestBody UpdateStatusRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.changeStatus(id, request.status(), jwt.getSubject());
    }
}