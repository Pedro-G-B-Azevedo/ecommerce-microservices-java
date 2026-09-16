package com.ecommerce.orders.exception;

import java.util.UUID;

/**
 * Lançada quando o mesmo produto aparece mais de uma vez no pedido. Deixar passar
 * violaria a unique key (order_id, product_id) só no banco, devolvendo 500 em vez
 * de um erro de validação.
 */
public class DuplicateOrderItemException extends RuntimeException {

    public DuplicateOrderItemException(UUID productId) {
        super("O produto aparece mais de uma vez no pedido: " + productId);
    }
}
