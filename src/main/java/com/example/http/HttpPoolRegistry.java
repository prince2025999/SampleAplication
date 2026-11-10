package com.example.http;

import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.PoolingHttpClientConnectionManagerMetricsBinder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Owns one {@link CloseableHttpClient} (and therefore one connection pool) per group.
 * The pool lives inside the HttpClient, so every RestClient built on the same
 * HttpClient shares the same connections.
 */
public class HttpPoolRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HttpPoolRegistry.class);

    private final Map<String, CloseableHttpClient> clients = new HashMap<>();
    private final Map<String, PoolingHttpClientConnectionManager> managers = new HashMap<>();

    public HttpPoolRegistry(HttpClientProperties props, MeterRegistry meterRegistry) {
        props.pools().forEach((name, pool) -> {
            PoolingHttpClientConnectionManager cm = buildConnectionManager(pool);
            managers.put(name, cm);
            clients.put(name, buildHttpClient(cm, pool));

            // Exposes httpcomponents.httpclient.pool.* metrics tagged httpclient=<pool name>
            if (meterRegistry != null) {
                new PoolingHttpClientConnectionManagerMetricsBinder(cm, name).bindTo(meterRegistry);
            }
            log.info("Created HTTP pool '{}' (maxTotal={}, maxPerRoute={})",
                    name, pool.maxTotal(), pool.maxPerRoute());
        });
    }

    private PoolingHttpClientConnectionManager buildConnectionManager(HttpClientProperties.Pool p) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(p.connectTimeout()))
                .setSocketTimeout(Timeout.of(p.socketTimeout()))
                .setTimeToLive(TimeValue.of(p.timeToLive()))
                .setValidateAfterInactivity(TimeValue.of(p.validateAfterInactivity()))
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(p.maxTotal())
                .setMaxConnPerRoute(p.maxPerRoute())
                .setDefaultConnectionConfig(connectionConfig)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .build();
    }

    private CloseableHttpClient buildHttpClient(PoolingHttpClientConnectionManager cm,
                                                HttpClientProperties.Pool p) {
        // Group-level defaults; each service's factory overrides what it needs per request.
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(p.connectionRequestTimeout()))
                .setResponseTimeout(Timeout.of(p.responseTimeout()))
                .build();

        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(defaultRequestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.of(p.idleEviction()))
                .build();
    }

    public CloseableHttpClient client(String pool) {
        CloseableHttpClient client = clients.get(pool);
        if (client == null) {
            throw new IllegalArgumentException("Unknown HTTP pool: " + pool + ", known: " + clients.keySet());
        }
        return client;
    }

    /** Exposed for monitoring / tests. */
    public PoolingHttpClientConnectionManager connectionManager(String pool) {
        return managers.get(pool);
    }

    /** Closes each pool exactly once on application shutdown. */
    @Override
    public void destroy() {
        clients.forEach((name, client) -> {
            log.info("Closing HTTP pool '{}'", name);
            client.close(CloseMode.GRACEFUL);
        });
    }
}
