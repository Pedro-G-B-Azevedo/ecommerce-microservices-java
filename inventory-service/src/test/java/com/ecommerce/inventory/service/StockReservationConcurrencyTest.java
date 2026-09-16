package com.ecommerce.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.inventory.dto.CreateProductRequest;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReservationItemRequest;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prova que a reserva não vende estoque inexistente sob concorrência.
 *
 * <p>Sem o {@code SELECT ... FOR UPDATE} do
 * {@code StockItemRepository#findByProductIdForUpdate}, as duas transações leriam a
 * mesma disponibilidade e ambas reservariam — este teste falharia com as duas
 * reservas bem-sucedidas e o estoque negativo (ou barrado pela check constraint).
 */
@SpringBootTest
class StockReservationConcurrencyTest extends PostgresContainerSupport {

    @Autowired
    private ProductService productService;

    @Autowired
    private StockReservationService reservationService;

    @Test
    @DisplayName("duas reservas simultâneas de 7 unidades sobre 10 disponíveis: só uma passa")
    void concurrentReservationsDoNotOversell() throws Exception {
        ProductResponse product = productService.create(new CreateProductRequest(
                "CONC-" + UUID.randomUUID(), "Produto concorrente", null, new BigDecimal("10.00"), 10));

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(reserveTask(product.id(), 7, startTogether));
            Future<Outcome> second = pool.submit(reserveTask(product.id(), 7, startTogether));

            List<Outcome> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                    .singleElement()
                    .satisfies(outcome -> assertThat(outcome.failure())
                            .isInstanceOf(InsufficientStockException.class));
        } finally {
            pool.shutdownNow();
        }

        StockResponse stock = productService.findStock(product.id());
        assertThat(stock.availableQuantity()).isEqualTo(3);
        assertThat(stock.reservedQuantity()).isEqualTo(7);
    }

    private Callable<Outcome> reserveTask(UUID productId, int quantity, CyclicBarrier barrier) {
        return () -> {
            barrier.await(30, TimeUnit.SECONDS);
            try {
                reservationService.reserve(new ReserveStockRequest(
                        UUID.randomUUID(), List.of(new ReservationItemRequest(productId, quantity))));
                return new Outcome(true, null);
            } catch (RuntimeException ex) {
                return new Outcome(false, ex);
            }
        };
    }

    private record Outcome(boolean succeeded, RuntimeException failure) {
    }
}
