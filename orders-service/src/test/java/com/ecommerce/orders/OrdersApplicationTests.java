package com.ecommerce.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe o contexto completo contra um PostgreSQL real e executa as migrations Flyway.
 * Serve como teste de fumaça da configuração do serviço.
 */
@SpringBootTest
@Testcontainers
class OrdersApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Falha se o contexto, o datasource ou as migrations estiverem quebrados.
    }
}
