package com.ecommerce.orders.config;

import com.ecommerce.orders.client.ServiceTokenProvider;
import com.ecommerce.orders.observability.CorrelationIdRestInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({InventoryProperties.class, ServiceAccountProperties.class})
public class InventoryClientConfig {

    /**
     * {@code RestClient} em vez de OpenFeign: o Spring Cloud OpenFeign está em modo
     * manutenção e o RestClient é nativo do Spring Framework, sem dependência extra.
     */
    /** Cliente usado apenas para obter o token de serviço no auth-service. */
    @Bean
    public RestClient authRestClient(RestClient.Builder builder, ServiceAccountProperties properties,
                                     CorrelationIdRestInterceptor correlationIdRestInterceptor) {
        return builder
                .baseUrl(properties.authBaseUrl())
                .requestInterceptor(correlationIdRestInterceptor)
                .build();
    }

    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder, InventoryProperties properties,
                                          ObjectProvider<ServiceTokenProvider> tokenProvider,
                                          CorrelationIdRestInterceptor correlationIdRestInterceptor) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // ObjectProvider quebra o ciclo: o ServiceTokenProvider depende de um
                // RestClient, que é criado por esta mesma configuração.
                .requestInitializer(request -> request.getHeaders()
                        .setBearerAuth(tokenProvider.getObject().currentToken()))
                .requestInterceptor(correlationIdRestInterceptor)
                .build();
    }
}
