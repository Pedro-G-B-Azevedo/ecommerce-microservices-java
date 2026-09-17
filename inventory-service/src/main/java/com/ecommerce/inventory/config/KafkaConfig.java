package com.ecommerce.inventory.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRIES = 3L;

    /**
     * Repete algumas vezes e, se não resolver, manda a mensagem para o
     * <em>dead-letter topic</em>.
     *
     * <p>Sem isso, o comportamento padrão do Kafka diante de uma mensagem que sempre
     * falha é reprocessá-la para sempre, travando a partição e impedindo o avanço de
     * todos os pedidos seguintes. O DLT tira a mensagem do caminho e a preserva para
     * análise.
     *
     * <p>Falhas de negócio (estoque insuficiente) não chegam aqui: são desfecho, não
     * erro, e viram um evento de rejeição.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> {
                    logFailure(record, exception);
                    recoverer.accept(record, exception);
                },
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        // Uma mensagem que nem sequer desserializa nunca vai desserializar: repetir
        // seria desperdício.
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class);
        return handler;
    }

    private static void logFailure(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Mensagem enviada ao dead-letter topic: topic={} partition={} offset={} motivo={}",
                record.topic(), record.partition(), record.offset(), exception.getMessage(), exception);
    }
}
