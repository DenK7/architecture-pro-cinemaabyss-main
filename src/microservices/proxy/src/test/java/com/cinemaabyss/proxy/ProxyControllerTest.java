package com.cinemaabyss.proxy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyControllerTest {

    private static final HttpServer MONOLITH = upstream("monolith");
    private static final HttpServer MOVIES = upstream("movies");

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("proxy.monolith-url", () -> url(MONOLITH));
        registry.add("proxy.movies-service-url", () -> url(MOVIES));
        registry.add("proxy.events-service-url", () -> "http://127.0.0.1:1");
        registry.add("proxy.gradual-migration", () -> "true");
        registry.add("proxy.movies-migration-percent", () -> "100");
    }

    @AfterAll
    static void stop() {
        MONOLITH.stop(0);
        MOVIES.stop(0);
    }

    @Test
    void healthIsServedByProxyItself() {
        ResponseEntity<String> response = rest.getForEntity("/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"status\":true}");
    }

    @Test
    void moviesAreForwardedWithQueryString() {
        ResponseEntity<String> response = rest.getForEntity("/api/movies?id=7", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("movies GET /api/movies?id=7 ");
        assertThat(response.getHeaders().getFirst(ProxyController.UPSTREAM_HEADER)).isEqualTo(url(MOVIES));
    }

    @Test
    void otherDomainsAreForwardedToMonolithWithBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/api/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"neo\"}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("monolith POST /api/users {\"username\":\"neo\"}");
    }

    @Test
    void unavailableUpstreamGivesBadGateway() {
        ResponseEntity<String> response = rest.getForEntity("/api/events/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    /** Заглушка сервиса: отвечает своим именем, методом, путём с query и телом запроса. */
    private static HttpServer upstream(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getRawQuery();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] reply = (name + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                        + (query == null ? "" : "?" + query) + " " + body).getBytes(StandardCharsets.UTF_8);
                int status = "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
                exchange.sendResponseHeaders(status, reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
