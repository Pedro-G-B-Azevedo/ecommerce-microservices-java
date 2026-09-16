package com.ecommerce.inventory.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Produto não encontrado: " + productId);
    }
}
