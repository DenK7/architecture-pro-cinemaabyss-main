package com.cinemaabyss.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Фасад системы: принимает все запросы и пересылает их в сервис, выбранный {@link MigrationRouter}.
 */
@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");

    static final String UPSTREAM_HEADER = "X-Proxy-Upstream";

    private final MigrationRouter router;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ProxyController(MigrationRouter router) {
        this.router = router;
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("status", true);
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String upstream = router.route(path);
        String query = request.getQueryString();
        URI target = URI.create(upstream + path + (query == null ? "" : "?" + query));

        log.info("{} {} -> {}", request.getMethod(), path, upstream);

        HttpRequest.Builder upstreamRequest = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(30))
                .method(request.getMethod(), bodyOf(request));
        for (String name : Collections.list(request.getHeaderNames())) {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                for (String value : Collections.list(request.getHeaders(name))) {
                    upstreamRequest.header(name, value);
                }
            }
        }

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(upstreamRequest.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            log.warn("{} {} -> {}: сервис недоступен: {}", request.getMethod(), path, upstream, e.toString());
            return badGateway(upstream);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return badGateway(upstream);
        }

        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, List.copyOf(values));
            }
        });
        headers.set(UPSTREAM_HEADER, upstream);
        return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
    }

    private static HttpRequest.BodyPublisher bodyOf(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private static ResponseEntity<byte[]> badGateway(String upstream) {
        String body = "{\"error\":\"upstream unavailable\",\"upstream\":\"" + upstream + "\"}";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .header(UPSTREAM_HEADER, upstream)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
