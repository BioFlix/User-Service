package bioflix.user.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final int batchSize;
    private final Duration keepPublishedFor;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                       @Value("${app.rabbitmq.exchange}") String exchange,
                       @Value("${app.outbox.batch-size:50}") int batchSize,
                       @Value("${app.outbox.keep-published-for:7d}") Duration keepPublishedFor) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.batchSize = batchSize;
        this.keepPublishedFor = keepPublishedFor;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextUnpublished(batchSize);
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.markPublished();
                log.info("Published {} (eventId {}, user {})", event.getEventType(), event.getId(), event.getAggregateId());
            } catch (RuntimeException ex) {
                event.markAttemptFailed(ex.getMessage());
                log.warn("Could not publish {} (eventId {}, attempt {}) – will retry: {}",
                        event.getEventType(), event.getId(), event.getAttempts(), ex.getMessage());
                break;
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldPublished() {
        int deleted = repository.deletePublishedBefore(Instant.now().minus(keepPublishedFor));
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, keepPublishedFor);
        }
    }

    private void send(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.getId().toString());
        properties.setType(event.getEventType());
        properties.setAppId("user-service");
        properties.setTimestamp(Date.from(event.getCreatedAt()));
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);

        rabbitTemplate.invoke(operations -> {
            operations.send(exchange, event.getEventType(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
