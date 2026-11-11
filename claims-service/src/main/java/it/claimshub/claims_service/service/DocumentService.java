package it.claimshub.claims_service.service;

import it.claimshub.claims_service.model.DocumentInfo;
import it.claimshub.claims_service.port.DocumentMetadataRepository;
import it.claimshub.claims_service.port.DocumentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    public record DocumentContent(DocumentInfo info, byte[] content) {}

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg");

    private final ClaimService claimService;
    private final DocumentStore store;
    private final DocumentMetadataRepository metadata;
    private final ClaimAudit audit;

    public Mono<DocumentInfo> upload(String claimId, FilePart file, String username) {
        MediaType mediaType = file.headers().getContentType();
        String contentType = mediaType == null ? "" : mediaType.getType() + "/" + mediaType.getSubtype();
        if (!ALLOWED_TYPES.contains(contentType)) {
            return Mono.error(new InvalidDocumentException(
                    "Unsupported file type (allowed: PDF, PNG, JPEG)"));
        }

        return claimService.findById(claimId)          // il sinistro deve esistere
                .then(readBytes(file))
                .flatMap(bytes -> {
                    if (bytes.length == 0) {
                        return Mono.error(new InvalidDocumentException("Empty file"));
                    }
                    String docId = UUID.randomUUID().toString();
                    DocumentInfo info = new DocumentInfo(docId, claimId, cleanName(file.filename()),
                            contentType, bytes.length, Instant.now(), username);
                    return store.put(docId, bytes)
                            .then(metadata.save(info))
                            .then(audit.record(claimId, "DOCUMENT_ADDED", info.fileName(), username))
                            .thenReturn(info);
                });
    }

    public Flux<DocumentInfo> list(String claimId) {
        return claimService.findById(claimId).thenMany(metadata.findByClaimId(claimId));
    }

    public Mono<DocumentContent> download(String claimId, String docId) {
        return claimService.findById(claimId)
                .then(metadata.findById(docId))
                .filter(info -> info.claimId().equals(claimId))
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(docId)))
                .flatMap(info -> store.get(info.id())
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(docId)))
                        .map(bytes -> new DocumentContent(info, bytes)));
    }

    private Mono<byte[]> readBytes(FilePart file) {
        return DataBufferUtils.join(file.content(), MAX_BYTES)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.toByteBuffer().get(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorMap(DataBufferLimitException.class, e -> new DocumentTooLargeException(MAX_BYTES))
                .defaultIfEmpty(new byte[0]);
    }

    // il nome arriva dal client: tengo solo caratteri innocui e limito la lunghezza
    private String cleanName(String raw) {
        String name = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        return name.isBlank() ? "document" : name;
    }
}