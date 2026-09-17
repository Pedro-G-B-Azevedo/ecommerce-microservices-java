package com.ecommerce.notification.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
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
     * <p>Sem isso, uma mensagem que sempre falha é reprocessada para sempre, travando
     * a partição e impedindo o avanço de todos os pedidos seguintes.
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

        // Uma mensagem que nem sequer desserializa nunca vai desserializar.
        handler.addNotRetryableExceptions(DeserializationException.class, MessageConversionException.class);
        return handler;
    }

    private static void logFailure(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Mensagem enviada ao dead-letter topic: topic={} partition={} offset={} motivo={}",
                record.topic(), record.partition(), record.offset(), exception.getMessage(), exception);
    }
}
