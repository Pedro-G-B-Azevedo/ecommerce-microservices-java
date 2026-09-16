package com.ecommerce.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.inventory.entity.StockItem;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regras de disponibilidade, testadas direto na entidade. */
class StockItemTest {

    @Test
    @DisplayName("reservar mais do que o disponível é recusado")
    void rejectsReserveBeyondAvailable() {
        StockItem stock = StockItem.create(UUID.randomUUID(), 3);

        assertThatThrownBy(() -> stock.reserve(4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(stock.getAvailableQuantity()).isEqualTo(3);
        assertThat(stock.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("reservar tudo o que existe é permitido e zera o disponível")
    void allowsReservingEverything() {
        StockItem stock = StockItem.create(UUID.randomUUID(), 3);

        stock.reserve(3);

        assertThat(stock.getAvailableQuantity()).isZero();
        assertThat(stock.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("confirmar reduz o reservado sem devolver ao disponível")
    void confirmDoesNotReturnUnits() {
        StockItem stock = StockItem.create(UUID.randomUUID(), 10);
        stock.reserve(4);

        stock.confirm(4);

        assertThat(stock.getAvailableQuantity()).isEqualTo(6);
        assertThat(stock.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("liberar mais do que está reservado é recusado")
    void rejectsReleasingMoreThanReserved() {
        StockItem stock = StockItem.create(UUID.randomUUID(), 10);
        stock.reserve(2);

        assertThatThrownBy(() -> stock.release(3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reservado insuficiente");
    }

    @Test
    @DisplayName("quantidade não positiva é recusada")
    void rejectsNonPositiveQuantity() {
        StockItem stock = StockItem.create(UUID.randomUUID(), 10);

        assertThatThrownBy(() -> stock.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.replenish(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
