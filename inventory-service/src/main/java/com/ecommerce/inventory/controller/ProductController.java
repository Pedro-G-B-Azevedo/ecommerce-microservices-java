package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.CreateProductRequest;
import com.ecommerce.inventory.dto.PageResponse;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReplenishStockRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Produtos", description = "Catálogo e controle de estoque")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Cadastra um produto",
            description = "Cria o produto e abre sua linha de estoque com a quantidade inicial informada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto cadastrado"),
            @ApiResponse(responseCode = "409", description = "SKU já cadastrado", content = @Content)
    })
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            UriComponentsBuilder uriBuilder) {

        ProductResponse created = productService.create(request);
        URI location = uriBuilder.path("/api/v1/products/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um produto pelo identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto encontrado"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public ProductResponse findById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @GetMapping("/by-ids")
    @Operation(summary = "Busca produtos em lote",
            description = "Consulta usada pelo orders-service ao montar um pedido, "
                    + "evitando uma chamada por item. Ids inexistentes são omitidos da resposta.")
    public List<ProductResponse> findByIds(
            @Parameter(description = "Identificadores separados por vírgula")
            @RequestParam List<UUID> ids) {
        return productService.findAllByIds(ids);
    }

    @GetMapping
    @Operation(summary = "Lista produtos",
            description = "Aceita busca textual por nome ou SKU e filtro por situação.")
    public PageResponse<ProductResponse> search(
            @Parameter(description = "Trecho do nome ou do SKU")
            @RequestParam(required = false) String term,

            @Parameter(description = "Filtra por produtos ativos ou inativos")
            @RequestParam(required = false) Boolean active,

            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return productService.search(term, active, pageable);
    }

    @GetMapping("/{id}/stock")
    @Operation(summary = "Consulta o estoque de um produto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estoque do produto"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public StockResponse findStock(@PathVariable UUID id) {
        return productService.findStock(id);
    }

    @PostMapping("/{id}/stock/replenish")
    @Operation(summary = "Dá entrada de estoque")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estoque atualizado"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content)
    })
    public StockResponse replenish(@PathVariable UUID id, @Valid @RequestBody ReplenishStockRequest request) {
        return productService.replenish(id, request);
    }
}
