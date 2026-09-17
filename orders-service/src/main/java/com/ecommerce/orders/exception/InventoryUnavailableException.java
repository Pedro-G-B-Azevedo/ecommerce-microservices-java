package com.ecommerce.orders.exception;

/**
 * Lançada quando o inventory-service não responde a tempo ou responde com erro.
 *
 * <p>É uma falha de infraestrutura, não do pedido: vira 503, e não 4xx, porque
 * repetir a mesma requisição mais tarde pode dar certo.
 */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(Throwable cause) {
        super("O serviço de catálogo está indisponível", cause);
    }
}
