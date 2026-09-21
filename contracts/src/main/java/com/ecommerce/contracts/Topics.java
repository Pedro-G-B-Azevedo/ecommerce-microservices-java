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

    /**
     * Destino único para mensagens que esgotaram as tentativas de reprocessamento,
     * qualquer que seja o tópico de origem.
     *
     * <p>O padrão do Spring Kafka é um DLT por tópico (ex.: "orders.order-created.DLT"),
     * o que exigiria seis tópicos ao todo neste projeto. Provedores gerenciados com
     * tier gratuito costumam limitar a quantidade de tópicos (a Aiven, por exemplo, a
     * cinco); consolidar num só mantém o projeto dentro desse limite sem abrir mão do
     * dead-letter topic.
     */
    public static final String DEAD_LETTER = "dead-letter-topic";

    private Topics() {
    }
}
