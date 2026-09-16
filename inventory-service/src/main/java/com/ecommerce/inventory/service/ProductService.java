package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.CreateProductRequest;
import com.ecommerce.inventory.dto.PageResponse;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReplenishStockRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.entity.Product;
import com.ecommerce.inventory.entity.StockItem;
import com.ecommerce.inventory.exception.DuplicateSkuException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.repository.ProductRepository;
import com.ecommerce.inventory.repository.ProductSpecifications;
import com.ecommerce.inventory.repository.StockItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;

    public ProductService(ProductRepository productRepository, StockItemRepository stockItemRepository) {
        this.productRepository = productRepository;
        this.stockItemRepository = stockItemRepository;
    }

    /** Cadastra o produto e abre sua linha de estoque na mesma transação. */
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = productRepository.save(Product.create(
                request.sku(), request.name(), request.description(), request.price()));
        stockItemRepository.save(StockItem.create(product.getId(), request.initialQuantity()));

        log.info("Produto {} cadastrado com SKU {} e {} unidades",
                product.getId(), product.getSku(), request.initialQuantity());
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID productId) {
        return ProductResponse.from(requireProduct(productId));
    }

    /**
     * Consulta em lote, usada pelo orders-service ao montar um pedido: uma chamada
     * para todos os itens, em vez de uma por item.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> findAllByIds(List<UUID> ids) {
        return productRepository.findAllByIdIn(ids).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String term, Boolean active, Pageable pageable) {
        List<Specification<Product>> filters = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            filters.add(ProductSpecifications.matches(term.trim()));
        }
        if (active != null) {
            filters.add(ProductSpecifications.isActive(active));
        }

        Page<Product> page = productRepository.findAll(Specification.allOf(filters), pageable);
        return PageResponse.from(page, ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public StockResponse findStock(UUID productId) {
        requireProduct(productId);
        return StockResponse.from(requireStock(productId));
    }

    @Transactional
    public StockResponse replenish(UUID productId, ReplenishStockRequest request) {
        requireProduct(productId);
        StockItem stock = requireStock(productId);
        stock.replenish(request.quantity());

        log.info("Estoque do produto {} reabastecido em {} unidades", productId, request.quantity());
        return StockResponse.from(stock);
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private StockItem requireStock(UUID productId) {
        return stockItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
