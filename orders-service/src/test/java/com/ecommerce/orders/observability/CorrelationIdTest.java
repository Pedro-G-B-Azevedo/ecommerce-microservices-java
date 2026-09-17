package com.ecommerce.orders.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("reaproveita o id recebido, quando existe")
    void reusesReceivedId() {
        assertThat(CorrelationId.currentOrGenerate("id-existente")).isEqualTo("id-existente");
    }

    @Test
    @DisplayName("gera um novo id quando nada foi recebido")
    void generatesWhenMissing() {
        assertThat(CorrelationId.currentOrGenerate(null)).isNotBlank();
        assertThat(CorrelationId.currentOrGenerate("  ")).isNotBlank();
        // Dois id gerados nunca deveriam colidir.
        assertThat(CorrelationId.currentOrGenerate(null)).isNotEqualTo(CorrelationId.currentOrGenerate(null));
    }

    @Test
    @DisplayName("anexa o id do MDC ao registro publicado no Kafka")
    void attachesMdcValueToRecord() {
        MDC.put(CorrelationId.MDC_KEY, "id-do-mdc");
        ProducerRecord<String, Object> record = new ProducerRecord<>("topico", "chave", "valor");

        CorrelationId.attach(record);

        assertThat(record.headers().lastHeader(CorrelationId.HEADER).value())
                .isEqualTo("id-do-mdc".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("não anexa nada quando o MDC está vazio")
    void attachesNothingWhenMdcIsEmpty() {
        ProducerRecord<String, Object> record = new ProducerRecord<>("topico", "chave", "valor");

        CorrelationId.attach(record);

        assertThat(record.headers().lastHeader(CorrelationId.HEADER)).isNull();
    }

    @Test
    @DisplayName("decodifica o header recebido de um consumidor Kafka")
    void decodesReceivedHeader() {
        byte[] header = "id-do-header".getBytes(StandardCharsets.UTF_8);
        assertThat(CorrelationId.decode(header)).isEqualTo("id-do-header");
    }

    @Test
    @DisplayName("gera um novo id quando o header do Kafka está ausente")
    void generatesWhenHeaderIsMissing() {
        String generated = CorrelationId.decode(null);
        assertThat(generated).isNotBlank();
        assertThat(UUID.fromString(generated)).isNotNull();
    }
}
