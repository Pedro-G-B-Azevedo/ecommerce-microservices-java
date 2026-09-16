package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.StockReservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    List<StockReservation> findAllByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
