package com.ecommerce.auth.observability;

import java.util.UUID;

/**
 * Um id que atravessa toda a jornada de um pedido: nasce na requisição HTTP,
 * segue nos headers do Kafka a cada salto da saga, viaja nas chamadas REST entre
 * serviços e aparece em cada linha de log em JSON — inclusive nos outros
 * serviços. Dado um id de correlação, dá para reconstruir a história completa de
 * um pedido com um grep, sem precisar de um sistema de tracing distribuído.
 *
 * <p>O auth-service não publica nem consome eventos, então esta versão só tem a
 * parte HTTP; orders, inventory e notification têm a versão completa, com os
 * métodos de header do Kafka.
 */
public final class CorrelationId {

    /** Nome usado tanto no cabeçalho HTTP quanto no header da mensagem Kafka. */
    public static final String HEADER = "X-Correlation-Id";

    /** Chave sob a qual o id fica no MDC, e por isso em cada linha de log JSON. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** Usado pelo filtro HTTP de entrada: aproveita o id recebido, ou gera um novo. */
    public static String currentOrGenerate(String received) {
        return (received == null || received.isBlank()) ? UUID.randomUUID().toString() : received;
    }
}
