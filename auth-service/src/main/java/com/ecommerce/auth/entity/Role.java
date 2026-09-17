package com.ecommerce.auth.entity;

/**
 * Papéis reconhecidos pelo sistema.
 *
 * <p>Vão no token como a claim {@code roles} e viram autoridades
 * {@code ROLE_<nome>} nos resource servers.
 */
public enum Role {

    /** Cria e acompanha os próprios pedidos. */
    CLIENTE,

    /** Administra o catálogo e enxerga os pedidos de todos os clientes. */
    ADMIN,

    /**
     * Identidade de um serviço, não de uma pessoa. Usada quando um serviço precisa
     * chamar outro sem que haja um usuário por trás — por exemplo, na compensação
     * disparada por um evento do Kafka.
     */
    SERVICE
}
