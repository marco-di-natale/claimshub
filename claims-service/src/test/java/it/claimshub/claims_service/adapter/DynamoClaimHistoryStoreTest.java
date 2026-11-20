package it.claimshub.claims_service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.claimshub.claims_service.model.ClaimEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@ExtendWith(MockitoExtension.class)
class DynamoClaimHistoryStoreTest {

    @Mock DynamoDbClient dynamo;
    DynamoClaimHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new DynamoClaimHistoryStore(dynamo, "tabella-di-prova");
    }

    private static Map<String, AttributeValue> item(String key, String timestamp, String type) {
        return Map.of(
                "claimId", AttributeValue.fromS("c1"),
                "eventKey", AttributeValue.fromS(key),
                "timestamp", AttributeValue.fromS(timestamp),
                "type", AttributeValue.fromS(type),
                "detail", AttributeValue.fromS("dettaglio"),
                "actor", AttributeValue.fromS("agent"));
    }

    @Test
    void append_putsItemWithClaimIdAndOrderedSortKey() {
        Instant ts = Instant.parse("2026-11-19T10:00:00.5Z");
        var event = new ClaimEvent("c1", ts, "CREATED", "Claim opened", "agent");

        StepVerifier.create(store.append(event)).verifyComplete();

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(captor.capture());
        PutItemRequest request = captor.getValue();
        assertEquals("tabella-di-prova", request.tableName());
        assertEquals("c1", request.item().get("claimId").s());
        assertEquals("CREATED", request.item().get("type").s());
        // la chiave di ordinamento parte da secondi e nanosecondi a larghezza fissa
        String prefix = String.format("%012d%09d#", ts.getEpochSecond(), ts.getNano());
        assertTrue(request.item().get("eventKey").s().startsWith(prefix));
    }

    @Test
    void findByClaimId_mapsItemsBackToEvents() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(item("k1", "2026-11-19T10:00:00.500Z", "CREATED")).build());

        StepVerifier.create(store.findByClaimId("c1"))
                .assertNext(e -> {
                    assertEquals("CREATED", e.type());
                    assertEquals("agent", e.actor());
                    assertEquals(Instant.parse("2026-11-19T10:00:00.500Z"), e.timestamp());
                })
                .verifyComplete();
    }

    @Test
    void findByClaimId_readsAllPages() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder()
                        .items(item("k1", "2026-11-19T10:00:00Z", "A"))
                        .lastEvaluatedKey(Map.of("claimId", AttributeValue.fromS("c1")))
                        .build(),
                QueryResponse.builder()
                        .items(item("k2", "2026-11-19T10:00:01Z", "B"))
                        .build());

        StepVerifier.create(store.findByClaimId("c1").map(ClaimEvent::type))
                .expectNext("A", "B")
                .verifyComplete();
    }

    @Test
    void append_isLazy_untilSubscribed() {
        Mono<Void> mono = store.append(new ClaimEvent("c1", Instant.now(), "X", "d", "a"));

        org.mockito.Mockito.verifyNoInteractions(dynamo);
        StepVerifier.create(mono).verifyComplete();
        verify(dynamo).putItem(any(PutItemRequest.class));
    }
}
