package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.ReservationItemRequest;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.entity.StockItem;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.exception.InvalidReservationStateException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.exception.ReservationNotFoundException;
import com.ecommerce.inventory.repository.StockItemRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva, confirmação e liberação de estoque.
 *
 * <p>A reserva é tudo ou nada: se faltar estoque para qualquer item, nenhum é
 * separado. E é idempotente por {@code orderId} — na etapa 4 o mesmo evento de
 * pedido pode ser entregue mais de uma vez.
 */
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository reservationRepository;

    public StockReservationService(StockItemRepository stockItemRepository,
                                   StockReservationRepository reservationRepository) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public ReservationResponse reserve(ReserveStockRequest request) {
        List<StockReservation> existing = reservationRepository.findAllByOrderId(request.orderId());
        if (!existing.isEmpty()) {
            // Reentrega do mesmo pedido: devolve a reserva que já existe em vez de
            // separar estoque de novo.
            log.info("Reserva do pedido {} já existia; requisição tratada como repetição", request.orderId());
            return ReservationResponse.from(existing);
        }

        Map<UUID, Integer> quantities = mergeByProduct(request.items());
        List<StockItem> locked = lockInDeterministicOrder(quantities.keySet());

        List<InsufficientStockException.Shortfall> shortfalls = new ArrayList<>();
        for (StockItem stock : locked) {
            int requested = quantities.get(stock.getProductId());
            if (!stock.hasAvailable(requested)) {
                shortfalls.add(new InsufficientStockException.Shortfall(
                        stock.getProductId(), requested, stock.getAvailableQuantity()));
            }
        }
        if (!shortfalls.isEmpty()) {
            // Sai antes de qualquer escrita: a transação inteira é descartada.
            throw new InsufficientStockException(shortfalls);
        }

        List<StockReservation> reservations = new ArrayList<>();
        for (StockItem stock : locked) {
            int requested = quantities.get(stock.getProductId());
            stock.reserve(requested);
            reservations.add(StockReservation.create(request.orderId(), stock.getProductId(), requested));
        }
        reservationRepository.saveAll(reservations);

        log.info("Reservados {} item(ns) para o pedido {}", reservations.size(), request.orderId());
        return ReservationResponse.from(reservations);
    }

    /** Confirma a saída definitiva do estoque reservado para o pedido. */
    @Transactional
    public ReservationResponse confirm(UUID orderId) {
        return settle(orderId, true);
    }

    /** Devolve ao estoque disponível tudo o que estava reservado para o pedido. */
    @Transactional
    public ReservationResponse release(UUID orderId) {
        return settle(orderId, false);
    }

    @Transactional(readOnly = true)
    public ReservationResponse findByOrderId(UUID orderId) {
        List<StockReservation> reservations = reservationRepository.findAllByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new ReservationNotFoundException(orderId);
        }
        return ReservationResponse.from(reservations);
    }

    private ReservationResponse settle(UUID orderId, boolean confirming) {
        List<StockReservation> reservations = reservationRepository.findAllByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new ReservationNotFoundException(orderId);
        }

        if (reservations.stream().noneMatch(StockReservation::isReserved)) {
            // Já resolvida antes: idempotente, devolve o estado atual.
            log.info("Reserva do pedido {} já estava resolvida como {}",
                    orderId, reservations.get(0).getStatus());
            return ReservationResponse.from(reservations);
        }

        for (StockReservation reservation : reservations) {
            StockItem stock = stockItemRepository.findByProductIdForUpdate(reservation.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(reservation.getProductId()));
            try {
                if (confirming) {
                    stock.confirm(reservation.getQuantity());
                    reservation.confirm();
                } else {
                    stock.release(reservation.getQuantity());
                    reservation.release();
                }
            } catch (IllegalStateException ex) {
                throw new InvalidReservationStateException(ex.getMessage());
            }
        }

        log.info("Reserva do pedido {} {}", orderId, confirming ? "confirmada" : "liberada");
        return ReservationResponse.from(reservations);
    }

    /**
     * Soma as quantidades por produto, caso o mesmo produto apareça em mais de um
     * item da requisição.
     */
    private static Map<UUID, Integer> mergeByProduct(List<ReservationItemRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (ReservationItemRequest item : items) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    /**
     * Trava as linhas de estoque sempre na mesma ordem (por id do produto).
     *
     * <p>Dois pedidos com os mesmos produtos em ordens diferentes travariam as linhas
     * em ordem cruzada e poderiam entrar em deadlock; uma ordem total elimina essa
     * possibilidade.
     */
    private List<StockItem> lockInDeterministicOrder(Iterable<UUID> productIds) {
        List<UUID> sorted = new ArrayList<>();
        productIds.forEach(sorted::add);
        sorted.sort(Comparator.naturalOrder());

        List<StockItem> locked = new ArrayList<>(sorted.size());
        for (UUID productId : sorted) {
            locked.add(stockItemRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId)));
        }
        return locked;
    }
}
