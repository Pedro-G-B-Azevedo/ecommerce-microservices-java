package com.ecommerce.orders.entity;

/**
 * Ciclo de vida de um pedido.
 *
 * <p>Um pedido nasce {@link #PENDING} e permanece assim até que o inventory-service
 * responda à tentativa de reserva de estoque (etapa 4): a resposta o leva a
 * {@link #CONFIRMED} ou {@link #REJECTED}. Enquanto estiver pendente, o cliente
 * ainda pode desistir, levando o pedido a {@link #CANCELLED}.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
