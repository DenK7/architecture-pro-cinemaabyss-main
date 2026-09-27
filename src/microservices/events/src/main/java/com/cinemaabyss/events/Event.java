package com.cinemaabyss.events;

/** Конверт события в топике: тип, идентификатор, время и исходные данные запроса. */
public record Event(
        String id,
        String type,
        String timestamp,
        Object payload) {
}
