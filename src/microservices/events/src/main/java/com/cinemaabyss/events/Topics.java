package com.cinemaabyss.events;

import java.util.List;

/** Топики Kafka, в которые сервис публикует события и из которых сам же их читает. */
final class Topics {

    static final String MOVIE = "movie-events";
    static final String USER = "user-events";
    static final String PAYMENT = "payment-events";

    static final List<String> ALL = List.of(MOVIE, USER, PAYMENT);

    private Topics() {
    }
}
