package com.ecommerce.inventory.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes que precisam de banco.
 *
 * <p>O container é iniciado uma única vez por JVM (padrão <em>singleton container</em>)
 * em vez de usar {@code @Testcontainers}/{@code @Container}, que param o container ao
 * fim de cada classe e fariam cada classe de teste pagar o custo de subir outro.
 * O encerramento fica por conta do Ryuk, ao fim da execução.
 */
public abstract class PostgresContainerSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
