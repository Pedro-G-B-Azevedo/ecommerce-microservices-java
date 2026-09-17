package com.ecommerce.orders.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base dos testes que precisam de PostgreSQL e Kafka.
 *
 * <p>Herda o container de banco e acrescenta um broker, ambos iniciados uma única
 * vez por JVM. A imagem é a mesma do docker-compose, em modo KRaft.
 */
public abstract class KafkaContainerSupport extends PostgresContainerSupport {

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
    }
}
