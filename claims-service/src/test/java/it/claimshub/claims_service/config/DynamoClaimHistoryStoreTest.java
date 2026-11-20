package it.claimshub.claims_service.config;

import it.claimshub.claims_service.adapter.DynamoClaimHistoryStore;
import it.claimshub.claims_service.model.ClaimEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoClaimHistoryStoreTest {

    @Mock
    DynamoDbClient dynamo;
    DynamoClaimHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new DynamoClaimHistoryStore(dynamo, "tabella-di-prova");
    }

    @Test
    void append_putsItemWithClaimIdAndOrderedSortKey() {
        var event = new ClaimEvent("c1", Instant.parse("2026-11-19T10:00:00.5Z"),
                "CREATED", "Claim opened", "agent");

        StepVerifier.create(store.append(event)).verifyComplete();

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(captor.capture());
        PutItemRequest request = captor.getValue();
        assertEquals("tabella-di-prova", request.tableName());
        assertEquals("c1", request.item().get("claimId").s());
        assertEquals("CREATED", request.item().get("type").s());
    }

    @Test
    void findByClaimId_mapsItemsBackToEvents() {
        var item = Map.of(
                "claimId", AttributeValue.fromS("c1"),
                "eventKey", AttributeValue.fromS("k1"),
                "timestamp", AttributeValue.fromS("2026-11-19T10:00:00.500Z"),
                "type", AttributeValue.fromS("CREATED"),
                "detail", AttributeValue.fromS("Claim opened"),
                "actor", AttributeValue.fromS("agent"));
        when(dynamo.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(item).build());

        StepVerifier.create(store.findByClaimId("c1"))
                .assertNext(e -> {
                    assertEquals("CREATED", e.type());
                    assertEquals("agent", e.actor());
                })
                .verifyComplete();
    }

    @Test
    void findByClaimId_readsAllPages() {
        var first = Map.of("claimId", AttributeValue.fromS("c1"), "eventKey", AttributeValue.fromS("k1"),
                "timestamp", AttributeValue.fromS("2026-11-19T10:00:00Z"), "type", AttributeValue.fromS("A"),
                "detail", AttributeValue.fromS("d"), "actor", AttributeValue.fromS("x"));
        var second = Map.of("claimId", AttributeValue.fromS("c1"), "eventKey", AttributeValue.fromS("k2"),
                "timestamp", AttributeValue.fromS("2026-11-19T10:00:01Z"), "type", AttributeValue.fromS("B"),
                "detail", AttributeValue.fromS("d"), "actor", AttributeValue.fromS("x"));
        when(dynamo.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(first).lastEvaluatedKey(Map.of("claimId", AttributeValue.fromS("c1"))).build(),
                QueryResponse.builder().items(second).build());

        StepVerifier.create(store.findByClaimId("c1").map(ClaimEvent::type))
                .expectNext("A", "B")
                .verifyComplete();
    }
}