package it.claimshub.claims_service.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.claimshub.claims_service.model.ClaimEvent;
import it.claimshub.claims_service.port.ClaimHistoryStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Storico dei sinistri su DynamoDB. Attivo solo con il profilo "aws".
 * Partition key = claimId, sort key = eventKey (a larghezza fissa, si ordina come testo).
 */
@Component
@Profile("aws")
public class DynamoClaimHistoryStore implements ClaimHistoryStore {

    private final DynamoDbClient dynamo;
    private final String table;

    public DynamoClaimHistoryStore(DynamoDbClient dynamo,
                                   @Value("${app.aws.dynamodb.table}") String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    @Override
    public Mono<Void> append(ClaimEvent event) {
        return Mono.fromRunnable(() -> dynamo.putItem(PutItemRequest.builder()
                        .tableName(table)
                        .item(toItem(event))
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Flux<ClaimEvent> findByClaimId(String claimId) {
        return Mono.fromCallable(() -> queryAll(claimId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    // DynamoDB risponde a pagine: si continua finché c'è una chiave di ripartenza
    private List<ClaimEvent> queryAll(String claimId) {
        List<ClaimEvent> events = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(table)
                    .keyConditionExpression("claimId = :c")
                    .expressionAttributeValues(Map.of(":c", AttributeValue.fromS(claimId)))
                    .scanIndexForward(true);
            if (start != null) {
                request.exclusiveStartKey(start);
            }
            QueryResponse response = dynamo.query(request.build());
            response.items().forEach(item -> events.add(fromItem(item)));
            start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (start != null);
        return events;
    }

    private static Map<String, AttributeValue> toItem(ClaimEvent e) {
        String eventKey = String.format("%012d%09d#%s",
                e.timestamp().getEpochSecond(), e.timestamp().getNano(), UUID.randomUUID());
        return Map.of(
                "claimId", AttributeValue.fromS(e.claimId()),
                "eventKey", AttributeValue.fromS(eventKey),
                "timestamp", AttributeValue.fromS(e.timestamp().toString()),
                "type", AttributeValue.fromS(e.type()),
                "detail", AttributeValue.fromS(e.detail()),
                "actor", AttributeValue.fromS(e.actor()));
    }

    private static ClaimEvent fromItem(Map<String, AttributeValue> item) {
        return new ClaimEvent(
                item.get("claimId").s(),
                Instant.parse(item.get("timestamp").s()),
                item.get("type").s(),
                item.get("detail").s(),
                item.get("actor").s());
    }
}
