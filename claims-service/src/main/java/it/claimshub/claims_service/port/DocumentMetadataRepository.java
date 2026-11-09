package it.claimshub.claims_service.port;

import it.claimshub.claims_service.model.DocumentInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentMetadataRepository {
    Mono<DocumentInfo> save(DocumentInfo info);
    Mono<DocumentInfo> findById(String id);
    Flux<DocumentInfo> findByClaimId(String claimId);
}