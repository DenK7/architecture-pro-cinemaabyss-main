package com.cinemaabyss.proxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * Выбирает, куда отправить запрос: в монолит или в выделенный микросервис.
 * <p>
 * Strangler Fig для домена фильмов: при включённом {@code GRADUAL_MIGRATION}
 * в movies-service уходит {@code MOVIES_MIGRATION_PERCENT} процентов запросов,
 * остальные — в монолит. При выключенном флаге домен целиком обслуживает movies-service.
 */
@Component
public class MigrationRouter {

    private static final String MOVIES_PATH = "/api/movies";
    private static final String EVENTS_PATH = "/api/events";

    private final String monolithUrl;
    private final String moviesServiceUrl;
    private final String eventsServiceUrl;
    private final boolean gradualMigration;
    private final int moviesMigrationPercent;
    private final IntSupplier percentile;

    @Autowired
    public MigrationRouter(
            @Value("${proxy.monolith-url}") String monolithUrl,
            @Value("${proxy.movies-service-url}") String moviesServiceUrl,
            @Value("${proxy.events-service-url}") String eventsServiceUrl,
            @Value("${proxy.gradual-migration}") boolean gradualMigration,
            @Value("${proxy.movies-migration-percent}") int moviesMigrationPercent) {
        this(monolithUrl, moviesServiceUrl, eventsServiceUrl, gradualMigration, moviesMigrationPercent,
                () -> ThreadLocalRandom.current().nextInt(100));
    }

    MigrationRouter(String monolithUrl, String moviesServiceUrl, String eventsServiceUrl,
                    boolean gradualMigration, int moviesMigrationPercent, IntSupplier percentile) {
        this.monolithUrl = trimSlash(monolithUrl);
        this.moviesServiceUrl = trimSlash(moviesServiceUrl);
        this.eventsServiceUrl = trimSlash(eventsServiceUrl);
        this.gradualMigration = gradualMigration;
        this.moviesMigrationPercent = Math.max(0, Math.min(100, moviesMigrationPercent));
        this.percentile = percentile;
    }

    /** Базовый адрес сервиса, который обработает запрос с данным путём. */
    public String route(String path) {
        if (matches(path, MOVIES_PATH)) {
            return routeMovies();
        }
        if (matches(path, EVENTS_PATH)) {
            return eventsServiceUrl;
        }
        return monolithUrl;
    }

    private String routeMovies() {
        if (!gradualMigration) {
            return moviesServiceUrl;
        }
        return percentile.getAsInt() < moviesMigrationPercent ? moviesServiceUrl : monolithUrl;
    }

    private static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
