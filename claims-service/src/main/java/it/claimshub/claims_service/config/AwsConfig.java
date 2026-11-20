package it.claimshub.claims_service.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Client AWS, attivi solo con il profilo "aws".
 * Con app.aws.endpoint impostato parlano con un emulatore locale (LocalStack);
 * senza endpoint parlerebbero con AWS vero, usando la catena di credenziali standard.
 */
@Configuration
@Profile("aws")
public class AwsConfig {

    @Bean
    S3Client s3Client(@Value("${app.aws.region}") String region,
                      @Value("${app.aws.endpoint:}") String endpoint) {
        S3ClientBuilder builder = common(S3Client.builder(), region, endpoint);
        if (!endpoint.isBlank()) {
            builder.forcePathStyle(true);   // l'emulatore non risolve "bucket.host"
        }
        return builder.build();
    }

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${app.aws.region}") String region,
                                  @Value("${app.aws.endpoint:}") String endpoint) {
        return common(DynamoDbClient.builder(), region, endpoint).build();
    }

    @Bean
    SqsClient sqsClient(@Value("${app.aws.region}") String region,
                        @Value("${app.aws.endpoint:}") String endpoint) {
        return common(SqsClient.builder(), region, endpoint).build();
    }

    private static <B extends AwsClientBuilder<B, ?>> B common(B builder, String region, String endpoint) {
        builder.region(Region.of(region));
        if (!endpoint.isBlank()) {
            // credenziali finte: valgono solo per l'emulatore
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")));
        }
        return builder;
    }
}
