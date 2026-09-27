package com.cinemaabyss.events;

public record EventResponse(
        String status,
        int partition,
        long offset,
        Event event) {
}
