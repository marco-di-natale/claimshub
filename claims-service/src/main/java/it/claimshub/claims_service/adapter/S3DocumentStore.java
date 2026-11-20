package it.claimshub.claims_service.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.claimshub.claims_service.port.DocumentStore;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Documenti su S3 (o su un emulatore). Attivo solo con il profilo "aws". */
@Component
@Profile("aws")
public class S3DocumentStore implements DocumentStore {

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStore(S3Client s3, @Value("${app.aws.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public Mono<Void> put(String key, byte[] content) {
        // il client S3 è bloccante: lo spostiamo sui thread adatti
        return Mono.fromRunnable(() -> s3.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        RequestBody.fromBytes(content)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<byte[]> get(String key) {
        return Mono.fromCallable(() -> s3.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray())
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(NoSuchKeyException.class, e -> Mono.empty());
    }
}
