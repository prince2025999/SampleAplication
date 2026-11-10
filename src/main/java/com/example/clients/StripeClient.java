package com.example.clients;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Option A: inject a RestClient bean declared in HttpClientConfig. */
@Service
public class StripeClient {

    private final RestClient restClient;

    public StripeClient(@Qualifier("stripeRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getBalance(String apiKey) {
        return restClient.get()
                .uri("/v1/balance")
                .headers(h -> h.setBearerAuth(apiKey))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
