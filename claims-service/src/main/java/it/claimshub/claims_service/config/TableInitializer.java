package it.claimshub.claims_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Component
@Profile("aws")
@RequiredArgsConstructor
@Slf4j
public class TableInitializer implements ApplicationRunner {

    private final DynamoDbClient dynamo;

    @Value("${app.aws.dynamodb.table}")
    private String table;

    @Override
    public void run(ApplicationArguments args) {
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
    }
}