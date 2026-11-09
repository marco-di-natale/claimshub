package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.model.DocumentInfo;
import it.claimshub.claims_service.port.DocumentMetadataRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDocumentMetadataRepository implements DocumentMetadataRepository {

    private final Map<String, DocumentInfo> store = new ConcurrentHashMap<>();

    @Override
    public Mono<DocumentInfo> save(DocumentInfo info) {
        return Mono.fromSupplier(() -> {
            store.put(info.id(), info);
            return info;
        });
    }

    @Override
    public Mono<DocumentInfo> findById(String id) {
        return Mono.fromSupplier(() -> store.get(id));
    }

    @Override
    public Flux<DocumentInfo> findByClaimId(String claimId) {
        return Flux.defer(() -> Flux.fromIterable(store.values()))
                .filter(d -> d.claimId().equals(claimId))
                .sort(Comparator.comparing(DocumentInfo::uploadedAt));
    }
}