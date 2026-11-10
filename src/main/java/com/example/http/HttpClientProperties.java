package com.example.http;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds the {@code http-client.*} section of application.yml.
 */
@ConfigurationProperties(prefix = "http-client")
public record HttpClientProperties(
        Map<String, Pool> pools,
        Map<String, Service> services) {

    public HttpClientProperties {
        pools = pools == null ? Map.of() : Map.copyOf(pools);
        services = services == null ? Map.of() : Map.copyOf(services);
    }

    /** One connection pool (= one CloseableHttpClient) shared by a group of services. */
    public record Pool(
            @DefaultValue("100") int maxTotal,
            @DefaultValue("20") int maxPerRoute,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("30s") Duration socketTimeout,
            @DefaultValue("5s") Duration responseTimeout,
            @DefaultValue("1s") Duration connectionRequestTimeout,
            @DefaultValue("5m") Duration timeToLive,
            @DefaultValue("30s") Duration idleEviction,
            @DefaultValue("2s") Duration validateAfterInactivity) {
    }

    /**
     * One external service. Timeouts are optional: when null, the pool's defaults apply.
     */
    public record Service(
            String pool,
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            Duration connectionRequestTimeout) {
    }
}
