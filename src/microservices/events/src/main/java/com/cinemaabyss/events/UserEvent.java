package com.cinemaabyss.events;

public record UserEvent(
        Long userId,
        String username,
        String email,
        String action,
        String timestamp) {
}
