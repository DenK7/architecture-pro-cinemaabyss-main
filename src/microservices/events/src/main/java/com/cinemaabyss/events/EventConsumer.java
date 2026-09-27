package com.cinemaabyss.events;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;

/**
 * Kafka consumer: в отдельном потоке читает топики событий и записывает обработку каждого события в лог.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private final KafkaConsumer<String, String> consumer;
    private final Thread thread = new Thread(this::run, "events-consumer");
    private volatile boolean running = true;

    public EventConsumer(@Value("${events.kafka.brokers}") String brokers,
                         @Value("${events.kafka.consumer-group}") String group) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        this.consumer = new KafkaConsumer<>(props);
    }

    @PostConstruct
    void start() {
        thread.start();
    }

    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        consumer.wakeup();
        thread.join(Duration.ofSeconds(5).toMillis());
    }

    private void run() {
        try (consumer) {
            consumer.subscribe(Topics.ALL);
            log.info("Подписка на топики {}", Topics.ALL);
            while (running) {
                for (ConsumerRecord<String, String> record : consumer.poll(POLL_TIMEOUT)) {
                    handle(record);
                }
            }
        } catch (WakeupException e) {
            if (running) {
                throw e;
            }
        } catch (RuntimeException e) {
            log.error("Консьюмер событий остановлен из-за ошибки", e);
        }
    }

    private void handle(ConsumerRecord<String, String> record) {
        log.info("Обработано событие из {}: partition={}, offset={}, key={}, value={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());
    }
}
