package com.example.clients;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.http.RestClientFactory;

/** Option B: build the RestClient once in the constructor from the factory. */
@Service
public class PartnerXClient {

    private final RestClient restClient;

    public PartnerXClient(RestClientFactory factory) {
        this.restClient = factory.forService("partner-x");   // shares the "partners" pool
    }

    public record Order(String id, String status) {}

    public Order getOrder(String id) {
        return restClient.get()
                .uri("/orders/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new IllegalStateException("Partner X returned " + res.getStatusCode());
                })
                .body(Order.class);
    }
}
