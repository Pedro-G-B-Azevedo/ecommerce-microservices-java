package com.ecommerce.inventory.observability;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;

/**
 * Um id que atravessa toda a jornada de um pedido: nasce na requisição HTTP,
 * segue nos headers do Kafka a cada salto da saga, viaja nas chamadas REST entre
 * serviços e aparece em cada linha de log em JSON — inclusive nos outros
 * serviços. Dado um id de correlação, dá para reconstruir a história completa de
 * um pedido com um grep, sem precisar de um sistema de tracing distribuído.
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

    /** Anexa o id corrente do MDC ao registro antes de publicar no Kafka. */
    public static void attach(ProducerRecord<String, ?> record) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null) {
            record.headers().add(HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Decodifica o header recebido de um consumidor Kafka.
     *
     * <p>Gera um novo id se o header estiver ausente — por exemplo, numa mensagem
     * injetada manualmente, sem passar pelo publisher — em vez de deixar a linha
     * de log sem nenhum id de correlação.
     */
    public static String decode(byte[] header) {
        return header == null ? UUID.randomUUID().toString() : new String(header, StandardCharsets.UTF_8);
    }
}
