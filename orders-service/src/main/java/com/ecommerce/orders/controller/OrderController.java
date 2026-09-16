package com.ecommerce.orders.controller;

import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.OrderResponse;
import com.ecommerce.orders.dto.OrderSummaryResponse;
import com.ecommerce.orders.dto.PageResponse;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/orders")
@Tag(name = "Pedidos", description = "Criação, consulta e cancelamento de pedidos")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Cria um pedido",
            description = "O pedido nasce com status PENDING. O total é calculado a partir dos itens.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriBuilder) {

        OrderResponse created = orderService.create(request);
        URI location = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um pedido pelo identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public OrderResponse findById(@PathVariable UUID id) {
        return orderService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Lista pedidos",
            description = "Aceita filtros opcionais por cliente e por status. Os itens não vêm na listagem.")
    public PageResponse<OrderSummaryResponse> search(
            @Parameter(description = "Filtra por cliente")
            @RequestParam(required = false) UUID customerId,

            @Parameter(description = "Filtra por status")
            @RequestParam(required = false) OrderStatus status,

            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return orderService.search(customerId, status, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela um pedido",
            description = "Só é possível cancelar pedidos que ainda estão pendentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "O pedido não está mais pendente", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public OrderResponse cancel(@PathVariable UUID id) {
        return orderService.cancel(id);
    }
}
