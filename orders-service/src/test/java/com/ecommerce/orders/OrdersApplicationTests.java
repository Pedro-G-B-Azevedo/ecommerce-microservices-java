package com.ecommerce.orders;

import com.ecommerce.orders.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto completo contra um PostgreSQL real e executa as migrations Flyway.
 * Serve como teste de fumaça da configuração do serviço.
 */
@SpringBootTest
class OrdersApplicationTests extends PostgresContainerSupport {

    @Test
    void contextLoads() {
        // Falha se o contexto, o datasource ou as migrations estiverem quebrados.
    }
}
