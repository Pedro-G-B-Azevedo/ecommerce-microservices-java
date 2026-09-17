package com.ecommerce.orders.observability;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Propaga o id de correlação corrente para as chamadas ao inventory-service e ao
 * auth-service, de modo que uma jornada completa — HTTP de entrada, saída para
 * outro serviço, Kafka, HTTP de volta — carregue sempre o mesmo id.
 */
@Component
public class CorrelationIdRestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().set(CorrelationId.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
