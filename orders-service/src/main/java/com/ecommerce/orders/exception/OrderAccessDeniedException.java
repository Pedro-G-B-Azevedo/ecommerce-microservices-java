package com.ecommerce.orders.exception;

/**
 * Pedido de outro cliente.
 *
 * <p>Tratada como 404, e não 403: confirmar que o pedido existe, mas pertence a
 * outra pessoa, já é informação que o solicitante não deveria obter.
 */
public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException() {
        super("Pedido não encontrado");
    }
}
