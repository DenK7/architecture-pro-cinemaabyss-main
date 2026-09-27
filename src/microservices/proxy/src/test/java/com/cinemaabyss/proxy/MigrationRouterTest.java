package com.cinemaabyss.proxy;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationRouterTest {

    private static final String MONOLITH = "http://monolith:8080";
    private static final String MOVIES = "http://movies-service:8081";
    private static final String EVENTS = "http://events-service:8082";

    private static MigrationRouter router(boolean gradual, int percent, int percentile) {
        return new MigrationRouter(MONOLITH, MOVIES, EVENTS, gradual, percent, () -> percentile);
    }

    @Test
    void nonMigratedDomainsGoToMonolith() {
        MigrationRouter router = router(true, 100, 0);

        assertThat(router.route("/api/users")).isEqualTo(MONOLITH);
        assertThat(router.route("/api/payments")).isEqualTo(MONOLITH);
        assertThat(router.route("/api/subscriptions")).isEqualTo(MONOLITH);
        assertThat(router.route("/api/moviesx")).isEqualTo(MONOLITH);
    }

    @Test
    void eventsGoToEventsService() {
        assertThat(router(true, 0, 0).route("/api/events/movie")).isEqualTo(EVENTS);
    }

    @Test
    void moviesGoToMicroserviceWhenFlagDisabled() {
        MigrationRouter router = router(false, 0, 99);

        assertThat(router.route("/api/movies")).isEqualTo(MOVIES);
        assertThat(router.route("/api/movies/health")).isEqualTo(MOVIES);
    }

    @Test
    void moviesSplitByPercentWhenFlagEnabled() {
        assertThat(router(true, 50, 49).route("/api/movies")).isEqualTo(MOVIES);
        assertThat(router(true, 50, 50).route("/api/movies")).isEqualTo(MONOLITH);
        assertThat(router(true, 0, 0).route("/api/movies")).isEqualTo(MONOLITH);
        assertThat(router(true, 100, 99).route("/api/movies")).isEqualTo(MOVIES);
    }

    @Test
    void percentShareMatchesConfiguration() {
        AtomicInteger next = new AtomicInteger();
        MigrationRouter router = new MigrationRouter(MONOLITH, MOVIES, EVENTS, true, 30,
                () -> next.getAndIncrement() % 100);

        int toMovies = 0;
        for (int i = 0; i < 1000; i++) {
            if (router.route("/api/movies").equals(MOVIES)) {
                toMovies++;
            }
        }
        assertThat(toMovies).isEqualTo(300);
    }
}
