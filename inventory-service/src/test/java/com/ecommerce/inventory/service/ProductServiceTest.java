package com.ecommerce.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.inventory.dto.CreateProductRequest;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReplenishStockRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.entity.Product;
import com.ecommerce.inventory.entity.StockItem;
import com.ecommerce.inventory.exception.DuplicateSkuException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.repository.ProductRepository;
import com.ecommerce.inventory.repository.StockItemRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @InjectMocks
    private ProductService service;

    @Test
    @DisplayName("cadastra o produto e abre a linha de estoque com a quantidade inicial")
    void createsProductAndOpensStock() {
        when(productRepository.existsBySku("TEC-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        ProductResponse response = service.create(new CreateProductRequest(
                "TEC-001", "Teclado", "ABNT2", new BigDecimal("349.90"), 50));

        assertThat(response.sku()).isEqualTo("TEC-001");
        assertThat(response.price()).isEqualByComparingTo("349.90");
        assertThat(response.active()).isTrue();

        ArgumentCaptor<StockItem> stock = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).save(stock.capture());
        assertThat(stock.getValue().getAvailableQuantity()).isEqualTo(50);
        assertThat(stock.getValue().getReservedQuantity()).isZero();
        assertThat(stock.getValue().getProductId()).isEqualTo(response.id());
    }

    @Test
    @DisplayName("recusa um SKU já cadastrado, sem gravar nada")
    void rejectsDuplicateSku() {
        when(productRepository.existsBySku("TEC-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateProductRequest(
                "TEC-001", "Teclado", null, new BigDecimal("349.90"), 50)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("TEC-001");

        verify(productRepository, never()).save(any());
        verify(stockItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("lança ProductNotFoundException ao buscar produto inexistente")
    void throwsWhenProductMissing() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(productId.toString());
    }

    @Test
    @DisplayName("entrada de estoque soma na quantidade disponível")
    void replenishAddsToAvailable() {
        Product product = Product.create("TEC-001", "Teclado", null, new BigDecimal("349.90"));
        StockItem stock = StockItem.create(product.getId(), 5);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(stockItemRepository.findById(product.getId())).thenReturn(Optional.of(stock));

        StockResponse response = service.replenish(product.getId(), new ReplenishStockRequest(25));

        assertThat(response.availableQuantity()).isEqualTo(30);
        assertThat(response.reservedQuantity()).isZero();
    }
}
