package it.claimshub.claims_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

@Component
@Profile("aws")
@RequiredArgsConstructor
@Slf4j
public class BucketInitializer implements ApplicationRunner {

    private final S3Client s3;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Bucket creato: {}", bucket);
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("Bucket già presente: {}", bucket);
        }
    }
}