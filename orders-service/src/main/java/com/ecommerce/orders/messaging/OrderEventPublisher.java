package com.ecommerce.orders.messaging;

import com.ecommerce.contracts.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica {@code OrderCreated} no Kafka, mas só depois que a transação do pedido
 * fizer commit.
 *
 * <p>Publicar antes anunciaria um pedido que ainda pode ser desfeito por rollback, e
 * o inventory-service separaria estoque para algo que nunca existiu.
 *
 * <p>Resta uma janela: se o processo cair entre o commit e o envio, o pedido fica
 * gravado como {@code PENDING} sem que o evento saia. É o custo consciente de não
 * usar o padrão <em>outbox</em>, que gravaria o evento na mesma transação e o
 * publicaria em seguida a partir da tabela. Fechar essa janela é o próximo passo
 * natural caso o projeto evolua.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderCreatedDomainEvent domainEvent) {
        var payload = domainEvent.payload();
        // A chave é o pedido: mantém a ordem dos eventos de um mesmo pedido.
        kafkaTemplate.send(Topics.ORDER_CREATED, payload.orderId().toString(), payload);
        log.info("Publicado OrderCreated {} para o pedido {}", payload.eventId(), payload.orderId());
    }
}
