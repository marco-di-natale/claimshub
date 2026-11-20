package it.claimshub.claims_service.adapter;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.claimshub.claims_service.port.NotificationPublisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Notifiche su una coda SQS. Attivo solo con il profilo "aws".
 * Il testo è nel corpo del messaggio; tipo e id del sinistro viaggiano come attributi,
 * così non serve costruire JSON a mano.
 */
@Component
@Profile("aws")
public class SqsNotificationPublisher implements NotificationPublisher {

    private final SqsClient sqs;
    private final String queueName;
    private volatile String queueUrl;

    public SqsNotificationPublisher(SqsClient sqs, @Value("${app.aws.sqs.queue}") String queueName) {
        this.sqs = sqs;
        this.queueName = queueName;
    }

    @Override
    public Mono<Void> publish(String type, String claimId, String message) {
        return Mono.fromRunnable(() -> sqs.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl())
                        .messageBody(message)
                        .messageAttributes(Map.of(
                                "type", attribute(type),
                                "claimId", attribute(claimId)))
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // l'indirizzo della coda si chiede una volta sola e poi si riusa
    private String queueUrl() {
        String url = queueUrl;
        if (url == null) {
            url = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
            queueUrl = url;
        }
        return url;
    }

    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
