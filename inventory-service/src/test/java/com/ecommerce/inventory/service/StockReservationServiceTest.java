package com.ecommerce.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.inventory.dto.ReservationItemRequest;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.entity.ReservationStatus;
import com.ecommerce.inventory.entity.StockItem;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.exception.ReservationNotFoundException;
import com.ecommerce.inventory.repository.StockItemRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    @InjectMocks
    private StockReservationService service;

    @Nested
    class Reserve {

        @Test
        @DisplayName("move as unidades de disponível para reservado")
        void movesUnitsToReserved() {
            UUID productId = UUID.randomUUID();
            StockItem stock = StockItem.create(productId, 10);
            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of());
            when(stockItemRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(stock));

            ReservationResponse response = service.reserve(request(new ReservationItemRequest(productId, 3)));

            assertThat(stock.getAvailableQuantity()).isEqualTo(7);
            assertThat(stock.getReservedQuantity()).isEqualTo(3);
            assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(response.items()).singleElement()
                    .satisfies(item -> assertThat(item.quantity()).isEqualTo(3));
        }

        @Test
        @DisplayName("não reserva nada quando falta estoque para um dos itens")
        void reservesNothingWhenAnyItemIsShort() {
            UUID plenty = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID scarce = UUID.fromString("00000000-0000-0000-0000-000000000002");
            StockItem plentyStock = StockItem.create(plenty, 10);
            StockItem scarceStock = StockItem.create(scarce, 1);

            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of());
            when(stockItemRepository.findByProductIdForUpdate(plenty)).thenReturn(Optional.of(plentyStock));
            when(stockItemRepository.findByProductIdForUpdate(scarce)).thenReturn(Optional.of(scarceStock));

            assertThatThrownBy(() -> service.reserve(request(
                    new ReservationItemRequest(plenty, 2),
                    new ReservationItemRequest(scarce, 5))))
                    .isInstanceOf(InsufficientStockException.class)
                    .satisfies(ex -> assertThat(((InsufficientStockException) ex).getShortfalls())
                            .singleElement()
                            .satisfies(shortfall -> {
                                assertThat(shortfall.productId()).isEqualTo(scarce);
                                assertThat(shortfall.requested()).isEqualTo(5);
                                assertThat(shortfall.available()).isEqualTo(1);
                            }));

            // O item que tinha estoque não pode ter sido tocado.
            assertThat(plentyStock.getAvailableQuantity()).isEqualTo(10);
            assertThat(plentyStock.getReservedQuantity()).isZero();
            verify(reservationRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("repetir o mesmo pedido devolve a reserva existente sem reservar de novo")
        void isIdempotentByOrderId() {
            UUID productId = UUID.randomUUID();
            StockReservation existing = StockReservation.create(ORDER_ID, productId, 3);
            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(existing));

            ReservationResponse response = service.reserve(request(new ReservationItemRequest(productId, 3)));

            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            verify(stockItemRepository, never()).findByProductIdForUpdate(any());
            verify(reservationRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("soma as quantidades quando o mesmo produto aparece duas vezes")
        void mergesRepeatedProduct() {
            UUID productId = UUID.randomUUID();
            StockItem stock = StockItem.create(productId, 10);
            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of());
            when(stockItemRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(stock));

            service.reserve(request(
                    new ReservationItemRequest(productId, 2),
                    new ReservationItemRequest(productId, 3)));

            assertThat(stock.getReservedQuantity()).isEqualTo(5);
            assertThat(stock.getAvailableQuantity()).isEqualTo(5);
        }
    }

    @Nested
    class Settle {

        @Test
        @DisplayName("confirmar retira as unidades reservadas do estoque")
        void confirmRemovesReservedUnits() {
            UUID productId = UUID.randomUUID();
            StockItem stock = StockItem.create(productId, 10);
            stock.reserve(4);
            StockReservation reservation = StockReservation.create(ORDER_ID, productId, 4);

            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(reservation));
            when(stockItemRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(stock));

            ReservationResponse response = service.confirm(ORDER_ID);

            assertThat(stock.getAvailableQuantity()).isEqualTo(6);
            assertThat(stock.getReservedQuantity()).isZero();
            assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("liberar devolve as unidades para disponível")
        void releaseReturnsUnitsToAvailable() {
            UUID productId = UUID.randomUUID();
            StockItem stock = StockItem.create(productId, 10);
            stock.reserve(4);
            StockReservation reservation = StockReservation.create(ORDER_ID, productId, 4);

            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(reservation));
            when(stockItemRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(stock));

            ReservationResponse response = service.release(ORDER_ID);

            assertThat(stock.getAvailableQuantity()).isEqualTo(10);
            assertThat(stock.getReservedQuantity()).isZero();
            assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        }

        @Test
        @DisplayName("confirmar duas vezes não mexe no estoque na segunda vez")
        void confirmIsIdempotent() {
            UUID productId = UUID.randomUUID();
            StockItem stock = StockItem.create(productId, 10);
            stock.reserve(4);
            StockReservation reservation = StockReservation.create(ORDER_ID, productId, 4);
            reservation.confirm();

            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(reservation));

            ReservationResponse response = service.confirm(ORDER_ID);

            assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(stock.getReservedQuantity()).isEqualTo(4);
            verify(stockItemRepository, never()).findByProductIdForUpdate(any());
        }

        @Test
        @DisplayName("lança ReservationNotFoundException quando o pedido não tem reserva")
        void throwsWhenReservationMissing() {
            when(reservationRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> service.confirm(ORDER_ID))
                    .isInstanceOf(ReservationNotFoundException.class);
        }
    }

    private static ReserveStockRequest request(ReservationItemRequest... items) {
        return new ReserveStockRequest(ORDER_ID, List.of(items));
    }
}
