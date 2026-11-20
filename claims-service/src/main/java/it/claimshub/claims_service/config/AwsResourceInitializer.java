package it.claimshub.claims_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

/**
 * Crea bucket, tabella e coda all'avvio. Serve solo con l'emulatore:
 * su AWS vero queste risorse si creano con infrastruttura come codice.
 */
@Component
@Profile("aws")
@RequiredArgsConstructor
@Slf4j
public class AwsResourceInitializer implements ApplicationRunner {

    private final S3Client s3;
    private final DynamoDbClient dynamo;
    private final SqsClient sqs;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Value("${app.aws.dynamodb.table}")
    private String table;

    @Value("${app.aws.sqs.queue}")
    private String queue;

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Bucket creato: {}", bucket);
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("Bucket già presente: {}", bucket);
        }

        try {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName(table)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("claimId")
                                    .attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("eventKey")
                                    .attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("claimId")
                                    .keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("eventKey")
                                    .keyType(KeyType.RANGE).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            log.info("Tabella creata: {}", table);
        } catch (ResourceInUseException e) {
            log.info("Tabella già presente: {}", table);
        }

        // createQueue è idempotente: se la coda esiste con gli stessi attributi restituisce quella
        sqs.createQueue(CreateQueueRequest.builder().queueName(queue).build());
        log.info("Coda pronta: {}", queue);
    }
}
