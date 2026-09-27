package com.cinemaabyss.events;

import java.util.List;

public record MovieEvent(
        Long movieId,
        String title,
        String action,
        Long userId,
        Double rating,
        List<String> genres,
        String description) {
}
