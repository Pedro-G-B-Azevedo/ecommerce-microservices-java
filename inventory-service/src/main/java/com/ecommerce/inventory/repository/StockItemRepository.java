package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.StockItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    /**
     * Carrega a linha de estoque com {@code SELECT ... FOR UPDATE}.
     *
     * <p>Reserva é leitura seguida de escrita condicional: sem travar a linha, duas
     * transações simultâneas leem a mesma disponibilidade e ambas reservam, vendendo
     * estoque que não existe. O bloqueio pessimista serializa as reservas do mesmo
     * produto; o otimista apenas faria uma delas falhar depois, exigindo retry.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.productId = :productId")
    Optional<StockItem> findByProductIdForUpdate(@Param("productId") UUID productId);
}
