package it.claimshub.claims_service.port;

import reactor.core.publisher.Mono;

public interface DocumentStore {
    Mono<Void> put(String key, byte[] content);
    Mono<byte[]> get(String key);        // vuoto se la chiave non esiste
}