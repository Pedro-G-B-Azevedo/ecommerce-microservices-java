package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.service.StockReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservas", description = "Reserva de estoque por pedido")
public class ReservationController {

    private final StockReservationService reservationService;

    public ReservationController(StockReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserva estoque para um pedido",
            description = "Tudo ou nada: se faltar estoque para qualquer item, nada é reservado. "
                    + "Repetir a chamada com o mesmo orderId devolve a reserva existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estoque reservado"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Estoque insuficiente", content = @Content)
    })
    public ReservationResponse reserve(@Valid @RequestBody ReserveStockRequest request) {
        return reservationService.reserve(request);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consulta a reserva de um pedido")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva encontrada"),
            @ApiResponse(responseCode = "404", description = "Não há reserva para o pedido", content = @Content)
    })
    public ReservationResponse findByOrderId(@PathVariable UUID orderId) {
        return reservationService.findByOrderId(orderId);
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Confirma a reserva",
            description = "As unidades reservadas deixam o estoque definitivamente.")
    public ReservationResponse confirm(@PathVariable UUID orderId) {
        return reservationService.confirm(orderId);
    }

    @PostMapping("/{orderId}/release")
    @Operation(summary = "Libera a reserva",
            description = "As unidades reservadas voltam a ficar disponíveis.")
    public ReservationResponse release(@PathVariable UUID orderId) {
        return reservationService.release(orderId);
    }
}
