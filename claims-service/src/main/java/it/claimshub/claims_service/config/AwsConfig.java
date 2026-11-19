package it.claimshub.claims_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@Profile("aws")
public class AwsConfig {

    @Bean
    S3Client s3Client(@Value("${app.aws.region}") String region,
                      @Value("${app.aws.endpoint:}") String endpoint) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            // emulatore locale: indirizzo esplicito, credenziali finte, path-style
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")))
                   .forcePathStyle(true);
        }
        // senza endpoint: AWS vero, con la catena di credenziali standard
        return builder.build();
    }
}