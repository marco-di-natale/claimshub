package it.claimshub.claims_service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@ExtendWith(MockitoExtension.class)
class SqsNotificationPublisherTest {

    @Mock SqsClient sqs;
    SqsNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SqsNotificationPublisher(sqs, "coda-di-prova");
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("http://q/url").build());
    }

    @Test
    void publish_sendsBodyAndAttributes_toResolvedQueue() {
        StepVerifier.create(publisher.publish("CREATED", "c1", "Claim opened")).verifyComplete();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertEquals("http://q/url", request.queueUrl());
        assertEquals("Claim opened", request.messageBody());
        assertEquals("CREATED", request.messageAttributes().get("type").stringValue());
        assertEquals("c1", request.messageAttributes().get("claimId").stringValue());
    }

    @Test
    void publish_resolvesQueueUrlOnlyOnce() {
        StepVerifier.create(publisher.publish("A", "c1", "uno")).verifyComplete();
        StepVerifier.create(publisher.publish("B", "c1", "due")).verifyComplete();

        verify(sqs, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
        verify(sqs, times(2)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void publish_isLazy_untilSubscribed() {
        var mono = publisher.publish("A", "c1", "uno");

        org.mockito.Mockito.verifyNoInteractions(sqs);
        StepVerifier.create(mono).verifyComplete();
    }
}
