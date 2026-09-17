package com.ecommerce.orders.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryClientConfig {

    /**
     * {@code RestClient} em vez de OpenFeign: o Spring Cloud OpenFeign está em modo
     * manutenção e o RestClient é nativo do Spring Framework, sem dependência extra.
     */
    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder, InventoryProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
