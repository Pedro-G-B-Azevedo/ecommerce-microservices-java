package com.ecommerce.notification.exception;

import java.util.UUID;

/**
 * Lançada quando chega o desfecho de um pedido cujo {@code OrderCreated} ainda não
 * foi visto.
 *
 * <p>Kafka só garante ordem dentro de uma partição, e os eventos de pedido e de
 * estoque vivem em tópicos diferentes — então a ordem entre eles não é garantida.
 * Na prática a criação vem antes, mas quando não vier, deixar a exceção subir faz o
 * Kafka reentregar a mensagem, e a tentativa seguinte encontra o cadastro.
 */
public class OrderContactNotFoundException extends RuntimeException {

    public OrderContactNotFoundException(UUID orderId) {
        super("Ainda não há cadastro de contato para o pedido: " + orderId);
    }
}
