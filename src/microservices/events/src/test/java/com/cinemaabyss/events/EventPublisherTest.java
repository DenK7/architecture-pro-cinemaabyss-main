package com.cinemaabyss.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventPublisherTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final Event event = new Event("user-5-logged_in", "user", "2026-09-27T10:00:00Z",
            new UserEvent(5L, "neo", null, "logged_in", "2026-09-27T10:00:00Z"));

    @Test
    void sendsJsonEventWithKeyToTopic() {
        MockProducer<String, String> producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());

        RecordMetadata metadata = new EventPublisher(producer, mapper).publish(Topics.USER, "5", event);

        assertThat(metadata.topic()).isEqualTo(Topics.USER);
        assertThat(producer.history()).hasSize(1);
        ProducerRecord<String, String> record = producer.history().get(0);
        assertThat(record.topic()).isEqualTo(Topics.USER);
        assertThat(record.key()).isEqualTo("5");
        assertThat(record.value()).isEqualTo("{\"id\":\"user-5-logged_in\",\"type\":\"user\","
                + "\"timestamp\":\"2026-09-27T10:00:00Z\",\"payload\":{\"user_id\":5,\"username\":\"neo\","
                + "\"action\":\"logged_in\",\"timestamp\":\"2026-09-27T10:00:00Z\"}}");
    }

    @Test
    void brokerErrorBecomesPublishException() {
        MockProducer<String, String> producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        producer.sendException = new KafkaException("broker down");

        assertThatThrownBy(() -> new EventPublisher(producer, mapper).publish(Topics.USER, "5", event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("user-5-logged_in");
    }
}
