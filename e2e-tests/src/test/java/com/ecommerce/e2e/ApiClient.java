package com.ecommerce.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente HTTP mínimo usado pelos testes ponta a ponta.
 *
 * <p>Deliberadamente sem Spring e sem nenhuma classe dos serviços: o teste enxerga as
 * APIs como qualquer cliente externo enxergaria. Se um contrato mudar, o teste quebra
 * — que é exatamente o que se espera dele.
 */
final class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;

    ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    Response get(String path, String token) {
        return send(request(path, token).GET());
    }

    Response post(String path, String json, String token) {
        return post(path, json, token, null, null);
    }

    /** Igual a {@link #post(String, String, String)}, com um cabeçalho extra — usado
     * para verificar a propagação do id de correlação de ponta a ponta. */
    Response post(String path, String json, String token, String extraHeaderName, String extraHeaderValue) {
        HttpRequest.Builder builder = request(path, token).header("Content-Type", "application/json");
        if (extraHeaderName != null) {
            builder.header(extraHeaderName, extraHeaderValue);
        }
        builder.POST(json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return send(builder);
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", ex);
        }
    }

    record Response(int status, String body, HttpHeaders headers) {

        JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (Exception ex) {
                throw new IllegalStateException("Resposta não é JSON válido: " + body, ex);
            }
        }

        String text(String field) {
            return json().path(field).asText();
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }
}
