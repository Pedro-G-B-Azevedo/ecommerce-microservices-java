package com.ecommerce.orders.exception;

/** Lançada quando a operação pedida não é válida para o status atual do pedido. */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
