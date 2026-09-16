package com.ecommerce.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.inventory.entity.Product;
import com.ecommerce.inventory.entity.StockItem;
import com.ecommerce.inventory.entity.StockReservation;
import com.ecommerce.inventory.support.PostgresContainerSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

/**
 * Exercita o mapeamento e as constraints contra o PostgreSQL real criado pelas
 * migrations Flyway — é o que garante que entidade e schema não divergem.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryPersistenceTest extends PostgresContainerSupport {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("persiste produto e estoque e preenche os campos de auditoria")
    void persistsProductAndStock() {
        Product product = productRepository.saveAndFlush(newProduct("TEC-001"));
        StockItem stock = stockItemRepository.saveAndFlush(StockItem.create(product.getId(), 50));

        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getVersion()).isZero();
        assertThat(product.isActive()).isTrue();
        assertThat(stock.getAvailableQuantity()).isEqualTo(50);
        assertThat(stock.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a unique key impede dois produtos com o mesmo SKU")
    void rejectsDuplicateSku() {
        productRepository.saveAndFlush(newProduct("TEC-DUP"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(newProduct("TEC-DUP")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a check constraint impede estoque disponível negativo")
    void rejectsNegativeAvailableQuantity() {
        Product product = productRepository.saveAndFlush(newProduct("TEC-NEG"));
        StockItem stock = stockItemRepository.saveAndFlush(StockItem.create(product.getId(), 1));

        // Escreve direto no banco, contornando as regras da entidade, para verificar
        // que o schema também protege o invariante — e não apenas o código Java.
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "update stock_items set available_quantity = -1 where product_id = :id")
                    .setParameter("id", stock.getProductId())
                    .executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("ck_stock_items_available_non_negative");
    }

    @Test
    @DisplayName("a unique key torna a reserva idempotente por (pedido, produto)")
    void rejectsDuplicateReservationForSameOrderAndProduct() {
        Product product = productRepository.saveAndFlush(newProduct("TEC-RES"));
        UUID orderId = UUID.randomUUID();
        reservationRepository.saveAndFlush(StockReservation.create(orderId, product.getId(), 2));

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(
                StockReservation.create(orderId, product.getId(), 3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("busca produtos por trecho do nome ou do SKU, sem diferenciar maiúsculas")
    void searchesByNameOrSku() {
        productRepository.saveAndFlush(newProduct("TEC-MEC-001", "Teclado mecânico"));
        productRepository.saveAndFlush(newProduct("MOU-001", "Mouse óptico"));
        productRepository.flush();

        assertThat(productRepository.findAll(
                ProductSpecifications.matches("teclado"), PageRequest.of(0, 10))).hasSize(1);
        assertThat(productRepository.findAll(
                ProductSpecifications.matches("TEC-"), PageRequest.of(0, 10))).hasSize(1);
        assertThat(productRepository.findAll(
                ProductSpecifications.matches("001"), PageRequest.of(0, 10))).hasSize(2);
    }

    @Test
    @DisplayName("consulta em lote devolve apenas os ids existentes")
    void findsByIdsIgnoringUnknown() {
        Product product = productRepository.saveAndFlush(newProduct("TEC-BATCH"));

        List<Product> found = productRepository.findAllByIdIn(
                List.of(product.getId(), UUID.randomUUID()));

        assertThat(found).singleElement()
                .satisfies(p -> assertThat(p.getSku()).isEqualTo("TEC-BATCH"));
    }

    private static Product newProduct(String sku) {
        return newProduct(sku, "Produto " + sku);
    }

    private static Product newProduct(String sku, String name) {
        return Product.create(sku, name, "Descrição", new BigDecimal("349.90"));
    }
}
