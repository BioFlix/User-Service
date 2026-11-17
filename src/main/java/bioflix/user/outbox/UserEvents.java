package bioflix.user.outbox;

import bioflix.user.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class UserEvents {

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_UPDATED = "user.updated";
    public static final String USER_DELETED = "user.deleted";

    private static final int VERSION = 1;
    private static final String PRODUCER = "user-service";

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public UserEvents(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public void registered(User user) {
        add(USER_REGISTERED, user.getId(), UserEventData.of(user));
    }

    public void updated(User user) {
        add(USER_UPDATED, user.getId(), UserEventData.of(user));
    }

    public void deleted(UUID userId) {
        add(USER_DELETED, userId, new UserEventData(userId, null, null, null, null));
    }

    private void add(String type, UUID userId, UserEventData data) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        var envelope = new EventEnvelope(eventId, type, VERSION, occurredAt, PRODUCER, userId.toString(), data);
        String payload = jsonMapper.writeValueAsString(envelope);
        repository.save(new OutboxEvent(eventId, userId, type, payload, occurredAt));
    }

    record EventEnvelope(UUID eventId, String type, int version, Instant occurredAt, String producer,
                         String correlationId, UserEventData data) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UserEventData(UUID userId, String email, String fullName, String phone, Boolean smsOptIn) {
        static UserEventData of(User user) {
            return new UserEventData(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                    user.isSmsOptIn());
        }
    }
}
