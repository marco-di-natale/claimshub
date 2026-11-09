package it.claimshub.claims_service.controller;

import it.claimshub.claims_service.model.*;
import it.claimshub.claims_service.service.ClaimService;
import it.claimshub.claims_service.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
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
    private final DocumentService documentService;

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

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentInfo> upload(@PathVariable String id,
                                     @RequestPart("file") Mono<FilePart> file,
                                     @AuthenticationPrincipal Jwt jwt) {
        return file.flatMap(f -> documentService.upload(id, f, jwt.getSubject()));
    }

    @GetMapping("/{id}/documents")
    public Flux<DocumentInfo> documents(@PathVariable String id) {
        return documentService.list(id);
    }

    @GetMapping("/{id}/documents/{docId}")
    public Mono<ResponseEntity<byte[]>> download(@PathVariable String id, @PathVariable String docId) {
        return documentService.download(id, docId).map(d -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.info().fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.content()));
    }
}