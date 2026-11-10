package com.example.http;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientConfig {

    @Bean
    HttpPoolRegistry httpPoolRegistry(HttpClientProperties props,
                                      ObjectProvider<MeterRegistry> meterRegistry) {
        return new HttpPoolRegistry(props, meterRegistry.getIfAvailable());
    }

    @Bean
    RestClientFactory restClientFactory(HttpPoolRegistry pools,
                                        HttpClientProperties props,
                                        ObjectProvider<RestClient.Builder> builders) {
        return new RestClientFactory(pools, props, builders);
    }

    // ------------------------------------------------------------------
    // Option A: declare RestClient beans here (inject with @Qualifier)
    // ------------------------------------------------------------------
    @Bean
    RestClient stripeRestClient(RestClientFactory factory) {
        return factory.forService("stripe");
    }

    @Bean
    RestClient adyenRestClient(RestClientFactory factory) {
        return factory.forService("adyen");
    }

    // Option B: call factory.forService("...") in each client's constructor
    // (see PartnerXClient) - no extra beans needed for 40 services.
}
