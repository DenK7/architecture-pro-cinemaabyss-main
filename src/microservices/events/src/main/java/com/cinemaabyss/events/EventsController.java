package com.cinemaabyss.events;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final EventPublisher publisher;

    public EventsController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("status", true);
    }

    @PostMapping("/movie")
    public ResponseEntity<EventResponse> movie(@RequestBody MovieEvent body) {
        require(body.movieId(), "movie_id");
        require(body.title(), "title");
        require(body.action(), "action");
        Event event = new Event("movie-" + body.movieId() + "-" + body.action(), "movie", now(), body);
        return publish(Topics.MOVIE, body.movieId(), event);
    }

    @PostMapping("/user")
    public ResponseEntity<EventResponse> user(@RequestBody UserEvent body) {
        require(body.userId(), "user_id");
        require(body.action(), "action");
        require(body.timestamp(), "timestamp");
        Event event = new Event("user-" + body.userId() + "-" + body.action(), "user", body.timestamp(), body);
        return publish(Topics.USER, body.userId(), event);
    }

    @PostMapping("/payment")
    public ResponseEntity<EventResponse> payment(@RequestBody PaymentEvent body) {
        require(body.paymentId(), "payment_id");
        require(body.userId(), "user_id");
        require(body.amount(), "amount");
        require(body.status(), "status");
        require(body.timestamp(), "timestamp");
        Event event = new Event("payment-" + body.paymentId() + "-" + body.status(), "payment", body.timestamp(), body);
        return publish(Topics.PAYMENT, body.paymentId(), event);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректный JSON"));
    }

    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<Map<String, String>> publishFailed(EventPublishException e) {
        log.error(e.getMessage(), e.getCause());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
    }

    private ResponseEntity<EventResponse> publish(String topic, Long key, Event event) {
        RecordMetadata metadata = publisher.publish(topic, String.valueOf(key), event);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new EventResponse("success", metadata.partition(), metadata.offset(), event));
    }

    private static void require(Object value, String field) {
        if (value == null || value instanceof String s && s.isBlank()) {
            throw new IllegalArgumentException("Не заполнено обязательное поле " + field);
        }
    }

    private static String now() {
        return Instant.now().toString();
    }
}
