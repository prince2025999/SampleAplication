package com.example.http;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds a {@link RestClient} per service:
 * <ul>
 *   <li>the HttpClient (= connection pool) is SHARED by all services in the same group</li>
 *   <li>the Spring request factory is PER SERVICE, so each service has its own timeouts</li>
 * </ul>
 */
public class RestClientFactory {

    private final HttpPoolRegistry pools;
    private final HttpClientProperties props;
    private final ObjectProvider<RestClient.Builder> builders;

    public RestClientFactory(HttpPoolRegistry pools,
                             HttpClientProperties props,
                             ObjectProvider<RestClient.Builder> builders) {
        this.pools = pools;
        this.props = props;
        this.builders = builders;
    }

    public RestClient forService(String serviceName) {
        HttpClientProperties.Service svc = props.services().get(serviceName);
        if (svc == null) {
            throw new IllegalArgumentException("Unknown service: " + serviceName
                    + ", known: " + props.services().keySet());
        }

        // NOT a Spring bean on purpose: if Spring managed it, it would call destroy()
        // and close the shared HttpClient. HttpPoolRegistry closes each pool once.
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(pools.client(svc.pool()));

        // Per-service overrides. Anything not set falls back to the pool's default RequestConfig.
        if (svc.connectTimeout() != null) {
            factory.setConnectTimeout(svc.connectTimeout());
        }
        if (svc.readTimeout() != null) {
            factory.setReadTimeout(svc.readTimeout());
        }
        if (svc.connectionRequestTimeout() != null) {
            factory.setConnectionRequestTimeout(svc.connectionRequestTimeout());
        }

        // RestClient.Builder is prototype-scoped in Spring Boot: getObject() returns a fresh
        // builder that already has Boot's observation (metrics/tracing) and message converters.
        return builders.getObject()
                .requestFactory(factory)
                .baseUrl(svc.baseUrl())
                .build();
    }
}
