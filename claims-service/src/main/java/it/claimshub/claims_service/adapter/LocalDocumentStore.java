package it.claimshub.claims_service.adapter;

import it.claimshub.claims_service.port.DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Component
@Profile("!aws")
public class LocalDocumentStore implements DocumentStore {

    private final Path root;

    public LocalDocumentStore(@Value("${app.documents.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    // protezione "path traversal": una chiave tipo ../../qualcosa non può uscire dalla cartella
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid key");
        }
        return path;
    }

    @Override
    public Mono<Void> put(String key, byte[] content) {
        return Mono.fromCallable(() -> Files.write(resolve(key), content))
                   .subscribeOn(Schedulers.boundedElastic())
                   .then();
    }

    @Override
    public Mono<byte[]> get(String key) {
        return Mono.fromCallable(() -> Files.readAllBytes(resolve(key)))
                   .subscribeOn(Schedulers.boundedElastic())
                   .onErrorResume(NoSuchFileException.class, e -> Mono.empty());
    }
}