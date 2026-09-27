package com.cinemaabyss.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Kafka producer: публикует событие и ждёт подтверждения записи в топик. */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);

    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventPublisher(@Value("${events.kafka.brokers}") String brokers, ObjectMapper objectMapper) {
        this(createProducer(brokers), objectMapper);
    }

    EventPublisher(Producer<String, String> producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    public RecordMetadata publish(String topic, String key, Event event) {
        String value;
        try {
            value = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventPublishException("Не удалось сериализовать событие " + event.id(), e);
        }

        try {
            RecordMetadata metadata = producer.send(new ProducerRecord<>(topic, key, value))
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Опубликовано событие {} в {}: partition={}, offset={}",
                    event.id(), topic, metadata.partition(), metadata.offset());
            return metadata;
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            throw new EventPublishException("Kafka не приняла событие " + event.id(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Публикация события " + event.id() + " прервана", e);
        }
    }

    @PreDestroy
    void close() {
        producer.close(Duration.ofSeconds(5));
    }

    private static Producer<String, String> createProducer(String brokers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
        return new KafkaProducer<>(props);
    }
}
