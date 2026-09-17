package com.ecommerce.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.orders.exception.InventoryUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Testa o cliente HTTP em si, sem subir o inventory-service. */
class InventoryClientTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockRestServiceServer server;
    private InventoryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InventoryClient(builder.build());
    }

    @Test
    @DisplayName("monta a consulta em lote e desserializa a resposta")
    void fetchesProductsInOneCall() {
        server.expect(requestTo("http://inventory/api/v1/products/by-ids?ids=" + PRODUCT_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":"11111111-1111-1111-1111-111111111111","sku":"TEC-001",
                          "name":"Teclado","description":"ABNT2","price":349.90,"active":true,
                          "createdAt":"2026-01-15T10:00:00Z","updatedAt":"2026-01-15T10:00:00Z"}]
                        """, MediaType.APPLICATION_JSON));

        List<ProductSnapshot> products = client.findByIds(List.of(PRODUCT_ID));

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.id()).isEqualTo(PRODUCT_ID);
            assertThat(product.price()).isEqualByComparingTo("349.90");
            assertThat(product.active()).isTrue();
        });
        server.verify();
    }

    @Test
    @DisplayName("campos que o orders-service não conhece são ignorados")
    void ignoresUnknownFields() {
        server.expect(requestTo("http://inventory/api/v1/products/by-ids?ids=" + PRODUCT_ID))
                .andRespond(withSuccess("""
                        [{"id":"11111111-1111-1111-1111-111111111111","sku":"TEC-001","name":"Teclado",
                          "price":10.00,"active":true,"campoNovoDoInventory":"qualquer coisa"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findByIds(List.of(PRODUCT_ID))).hasSize(1);
    }

    @Test
    @DisplayName("erro do inventory vira InventoryUnavailableException")
    void wrapsServerError() {
        server.expect(requestTo("http://inventory/api/v1/products/by-ids?ids=" + PRODUCT_ID))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.findByIds(List.of(PRODUCT_ID)))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    @DisplayName("lista vazia não gera chamada de rede")
    void doesNotCallForEmptyList() {
        assertThat(client.findByIds(List.of())).isEmpty();
        server.verify();
    }
}
