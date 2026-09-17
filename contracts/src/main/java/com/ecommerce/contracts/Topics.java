package com.ecommerce.contracts;

/**
 * Nomes dos tópicos trocados entre os serviços.
 *
 * <p>O prefixo identifica o serviço dono do evento: quem publica é responsável pelo
 * contrato, e quem consome não pode mudá-lo.
 */
public final class Topics {

    /** Publicado pelo orders-service quando um pedido é criado. */
    public static final String ORDER_CREATED = "orders.order-created";

    /** Publicado pelo inventory-service quando consegue separar o estoque. */
    public static final String STOCK_RESERVED = "inventory.stock-reserved";

    /** Publicado pelo inventory-service quando não consegue atender o pedido. */
    public static final String STOCK_REJECTED = "inventory.stock-rejected";

    private Topics() {
    }
}
