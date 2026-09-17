package com.ecommerce.orders.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.orders.dto.CreateOrderRequest;
import com.ecommerce.orders.dto.OrderItemRequest;
import com.ecommerce.orders.dto.OrderItemResponse;
import com.ecommerce.orders.dto.OrderResponse;
import com.ecommerce.orders.entity.OrderStatus;
import com.ecommerce.orders.exception.InvalidOrderStateException;
import com.ecommerce.orders.exception.InventoryUnavailableException;
import com.ecommerce.orders.exception.ProductUnavailableException;
import com.ecommerce.orders.exception.OrderNotFoundException;
import com.ecommerce.orders.service.OrderService;
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

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("POST devolve 201 com o cabeçalho Location apontando para o pedido")
    void createReturns201WithLocation() throws Exception {
        when(orderService.create(any())).thenReturn(sampleResponse(OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(299.80));
    }

    @Test
    @DisplayName("POST sem itens devolve 400 no formato Problem Details, com os campos inválidos")
    void createWithoutItemsReturns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(CUSTOMER_ID, List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    @DisplayName("POST com quantidade zero devolve 400")
    void createWithZeroQuantityReturns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(CUSTOMER_ID,
                List.of(new OrderItemRequest(PRODUCT_ID, 0)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    @DisplayName("GET de pedido inexistente devolve 404 no formato Problem Details")
    void findByIdReturns404() throws Exception {
        when(orderService.findById(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pedido não encontrado"))
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.com/problems/order-not-found"));
    }

    @Test
    @DisplayName("cancelar pedido que não está pendente devolve 409")
    void cancelReturns409() throws Exception {
        when(orderService.cancel(ORDER_ID))
                .thenThrow(new InvalidOrderStateException("Apenas pedidos pendentes podem ser cancelados"));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Transição de status inválida"));
    }

    @Test
    @DisplayName("produto fora do catálogo devolve 422 com os ids problemáticos")
    void unknownProductReturns422() throws Exception {
        when(orderService.create(any())).thenThrow(new ProductUnavailableException(List.of(PRODUCT_ID)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Produto indisponível"))
                .andExpect(jsonPath("$.productIds[0]").value(PRODUCT_ID.toString()));
    }

    @Test
    @DisplayName("catálogo fora do ar devolve 503, não 4xx")
    void inventoryOutageReturns503() throws Exception {
        when(orderService.create(any()))
                .thenThrow(new InventoryUnavailableException(new RuntimeException("timeout")));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.com/problems/inventory-unavailable"));
    }

    private static CreateOrderRequest validRequest() {
        return new CreateOrderRequest(CUSTOMER_ID,
                List.of(new OrderItemRequest(PRODUCT_ID, 2)));
    }

    private static OrderResponse sampleResponse(OrderStatus status) {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        return new OrderResponse(
                ORDER_ID,
                CUSTOMER_ID,
                status,
                new BigDecimal("299.80"),
                List.of(new OrderItemResponse(
                        PRODUCT_ID, 2, new BigDecimal("149.90"), new BigDecimal("299.80"))),
                now,
                now);
    }
}
