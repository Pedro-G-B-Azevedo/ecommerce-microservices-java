package com.ecommerce.inventory.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.inventory.dto.CreateProductRequest;
import com.ecommerce.inventory.dto.ProductResponse;
import com.ecommerce.inventory.dto.ReservationItemRequest;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.exception.DuplicateSkuException;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import com.ecommerce.inventory.service.ProductService;
import com.ecommerce.inventory.service.StockReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ProductController.class, ReservationController.class})
class InventoryControllerTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private StockReservationService reservationService;

    @Test
    @DisplayName("POST de produto devolve 201 com Location")
    void createProductReturns201() throws Exception {
        when(productService.create(any())).thenReturn(sampleProduct());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "TEC-001", "Teclado", null, new BigDecimal("349.90"), 50))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/" + PRODUCT_ID))
                .andExpect(jsonPath("$.sku").value("TEC-001"));
    }

    @Test
    @DisplayName("SKU repetido devolve 409")
    void duplicateSkuReturns409() throws Exception {
        when(productService.create(any())).thenThrow(new DuplicateSkuException("TEC-001"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "TEC-001", "Teclado", null, new BigDecimal("349.90"), 50))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("SKU já cadastrado"));
    }

    @Test
    @DisplayName("preço negativo devolve 400 com o campo inválido")
    void negativePriceReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "TEC-001", "Teclado", null, new BigDecimal("-1.00"), 50))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }

    @Test
    @DisplayName("produto inexistente devolve 404")
    void unknownProductReturns404() throws Exception {
        when(productService.findById(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.com/problems/product-not-found"));
    }

    @Test
    @DisplayName("estoque insuficiente devolve 409 com a lista de faltas")
    void insufficientStockReturns409WithShortfalls() throws Exception {
        when(reservationService.reserve(any())).thenThrow(new InsufficientStockException(
                List.of(new InsufficientStockException.Shortfall(PRODUCT_ID, 5, 1))));

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReserveStockRequest(
                                ORDER_ID, List.of(new ReservationItemRequest(PRODUCT_ID, 5))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Estoque insuficiente"))
                .andExpect(jsonPath("$.shortfalls[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.shortfalls[0].requested").value(5))
                .andExpect(jsonPath("$.shortfalls[0].available").value(1));
    }

    @Test
    @DisplayName("reserva sem itens devolve 400")
    void emptyReservationReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReserveStockRequest(ORDER_ID, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    private static ProductResponse sampleProduct() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        return new ProductResponse(PRODUCT_ID, "TEC-001", "Teclado", "ABNT2",
                new BigDecimal("349.90"), true, now, now);
    }
}
