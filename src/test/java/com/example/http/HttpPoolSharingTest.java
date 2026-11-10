package com.example.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Two services share one pool but have different read timeouts.
 * The local server answers /slow after 1.5s:
 *  - "fast" (read-timeout 500ms) must time out
 *  - "patient" (read-timeout 3s) must succeed
 * Both go through the same connection manager.
 */
class HttpPoolSharingTest {

    private HttpServer server;
    private HttpPoolRegistry registry;
    private RestClientFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        HttpClientProperties.Pool pool = new HttpClientProperties.Pool(
                10, 5, Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(2));

        HttpClientProperties props = new HttpClientProperties(
                Map.of("shared", pool),
                Map.of(
                        "fast", new HttpClientProperties.Service("shared", baseUrl,
                                Duration.ofSeconds(1), Duration.ofMillis(500), null),
                        "patient", new HttpClientProperties.Service("shared", baseUrl,
                                Duration.ofSeconds(1), Duration.ofSeconds(3), null)));

        registry = new HttpPoolRegistry(props, null);
        // No Spring context in this unit test, so supply plain builders.
        factory = new RestClientFactory(registry, props, new SingleBuilderProvider());
    }

    @AfterEach
    void tearDown() {
        registry.destroy();
        server.stop(0);
    }

    @Test
    void servicesSharePoolButHaveOwnReadTimeouts() {
        RestClient fast = factory.forService("fast");
        RestClient patient = factory.forService("patient");

        assertThatThrownBy(() -> fast.get().uri("/slow").retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class);

        assertThat(patient.get().uri("/slow").retrieve().body(String.class)).isEqualTo("ok");

        // Both services used the same connection manager (one pool).
        var stats = registry.connectionManager("shared").getTotalStats();
        assertThat(stats.getMax()).isEqualTo(10);
    }

    /** Minimal ObjectProvider returning a new builder each time (like Spring Boot's prototype bean). */
    static class SingleBuilderProvider
            implements org.springframework.beans.factory.ObjectProvider<RestClient.Builder> {
        @Override
        public RestClient.Builder getObject() {
            return RestClient.builder();
        }

        @Override
        public RestClient.Builder getObject(Object... args) {
            return RestClient.builder();
        }

        @Override
        public RestClient.Builder getIfAvailable() {
            return RestClient.builder();
        }

        @Override
        public RestClient.Builder getIfUnique() {
            return RestClient.builder();
        }
    }
}
