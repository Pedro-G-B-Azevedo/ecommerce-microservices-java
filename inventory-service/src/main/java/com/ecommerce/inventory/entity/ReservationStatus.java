package com.ecommerce.inventory.entity;

public enum ReservationStatus {

    /** Unidades separadas, aguardando o desfecho do pedido. */
    RESERVED,

    /** Pedido confirmado: as unidades saíram do estoque. */
    CONFIRMED,

    /** Pedido cancelado ou rejeitado: as unidades voltaram para disponível. */
    RELEASED
}
