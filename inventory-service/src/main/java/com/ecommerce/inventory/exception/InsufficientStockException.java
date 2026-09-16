package com.ecommerce.inventory.exception;

import java.util.List;
import java.util.UUID;

/**
 * Lançada quando ao menos um item do pedido não tem estoque suficiente.
 *
 * <p>Carrega a lista completa de faltas, e não apenas a primeira: na etapa 4 esse
 * detalhe vai no evento de rejeição, e devolver as faltas de uma vez evita que o
 * cliente descubra os problemas um a um.
 */
public class InsufficientStockException extends RuntimeException {

    private final List<Shortfall> shortfalls;

    public InsufficientStockException(List<Shortfall> shortfalls) {
        super("Estoque insuficiente para " + shortfalls.size() + " item(ns) do pedido");
        this.shortfalls = List.copyOf(shortfalls);
    }

    public List<Shortfall> getShortfalls() {
        return shortfalls;
    }

    public record Shortfall(UUID productId, int requested, int available) {
    }
}
