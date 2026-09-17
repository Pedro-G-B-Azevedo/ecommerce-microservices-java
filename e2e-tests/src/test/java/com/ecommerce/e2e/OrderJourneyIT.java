package com.ecommerce.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Percorre a jornada completa de um pedido atravessando os quatro serviços.
 *
 * <p>É o único teste que exercita a integração real entre todos eles: autenticação
 * no auth-service, catálogo e estoque no inventory-service, o pedido no
 * orders-service, a saga pelo Kafka e as notificações no notification-service. Os
 * demais testes cobrem cada serviço isoladamente e não pegariam, por exemplo, uma
 * divergência de contrato entre dois deles.
 */
class OrderJourneyIT {

    private static final Duration SAGA_TIMEOUT = Duration.ofSeconds(60);

    private static String adminToken;

    @BeforeAll
    static void authenticateAdmin() {
        adminToken = login(Services.ADMIN_EMAIL, Services.ADMIN_PASSWORD);
    }

    @Test
    @DisplayName("pedido com estoque disponível: confirmado e notificado")
    void confirmedOrderJourney() {
        String productId = createProduct(10);
        Customer ana = registerCustomer("ana");

        // 1. O pedido nasce pendente.
        ApiClient.Response created = Services.ORDERS.post("/api/v1/orders",
                orderBody(productId, 3), ana.token());
        assertThat(created.status()).isEqualTo(201);
        String orderId = created.text("id");
        assertThat(created.text("status")).isEqualTo("PENDING");

        // O preço veio do catálogo, não do cliente: 3 x 100.00.
        assertThat(created.json().path("totalAmount").asDouble()).isEqualTo(300.00);
        // E o pedido pertence a quem apresentou o token.
        assertThat(created.text("customerId")).isEqualTo(ana.id());

        // 2. A saga o leva a CONFIRMED.
        await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(orderStatus(orderId, ana.token())).isEqualTo("CONFIRMED"));

        // 3. O estoque saiu do disponível e ficou reservado.
        JsonNode stock = Services.INVENTORY.get("/api/v1/products/" + productId + "/stock", null).json();
        assertThat(stock.path("availableQuantity").asInt()).isEqualTo(7);
        assertThat(stock.path("reservedQuantity").asInt()).isEqualTo(3);

        // 4. O cliente foi avisado duas vezes.
        await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(notificationTypes(orderId, ana.token()))
                        .containsExactlyInAnyOrder("ORDER_RECEIVED", "ORDER_CONFIRMED"));
    }

    @Test
    @DisplayName("pedido sem estoque: rejeitado, estoque intocado e cliente avisado do motivo")
    void rejectedOrderJourney() {
        String productId = createProduct(2);
        Customer bruno = registerCustomer("bruno");

        String orderId = Services.ORDERS.post("/api/v1/orders", orderBody(productId, 99), bruno.token())
                .text("id");

        await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(orderStatus(orderId, bruno.token())).isEqualTo("REJECTED"));

        JsonNode order = Services.ORDERS.get("/api/v1/orders/" + orderId, bruno.token()).json();
        assertThat(order.path("rejectionReason").asText()).contains("Estoque insuficiente");

        JsonNode stock = Services.INVENTORY.get("/api/v1/products/" + productId + "/stock", null).json();
        assertThat(stock.path("availableQuantity").asInt()).isEqualTo(2);
        assertThat(stock.path("reservedQuantity").asInt()).isZero();

        await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(notificationTypes(orderId, bruno.token()))
                        .contains("ORDER_REJECTED"));

        JsonNode rejection = notifications(orderId, bruno.token()).valueStream()
                .filter(n -> "ORDER_REJECTED".equals(n.path("type").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(rejection.path("body").asText()).contains("pedidas 99, disponíveis 2");
    }

    @Test
    @DisplayName("um cliente não enxerga o pedido de outro")
    void ordersAreIsolatedBetweenCustomers() {
        String productId = createProduct(5);
        Customer dona = registerCustomer("dona");
        Customer intrusa = registerCustomer("intrusa");

        String orderId = Services.ORDERS.post("/api/v1/orders", orderBody(productId, 1), dona.token())
                .text("id");

        assertThat(Services.ORDERS.get("/api/v1/orders/" + orderId, dona.token()).status()).isEqualTo(200);
        // 404 e não 403: confirmar que o pedido existe já seria informação demais.
        assertThat(Services.ORDERS.get("/api/v1/orders/" + orderId, intrusa.token()).status()).isEqualTo(404);
        assertThat(Services.ORDERS.get("/api/v1/orders/" + orderId, adminToken).status()).isEqualTo(200);

        // O filtro por cliente na listagem é imposto, não aceito da requisição.
        JsonNode alheios = Services.ORDERS
                .get("/api/v1/orders?customerId=" + dona.id(), intrusa.token()).json();
        assertThat(alheios.path("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("o catálogo é público, mas alterá-lo exige ADMIN")
    void catalogIsReadableByAnyoneAndWritableByAdminOnly() {
        Customer cliente = registerCustomer("curioso");
        String novoProduto = """
                {"sku":"E2E-%s","name":"Produto","price":10.00,"initialQuantity":1}"""
                .formatted(UUID.randomUUID());

        assertThat(Services.INVENTORY.get("/api/v1/products", null).status()).isEqualTo(200);
        assertThat(Services.INVENTORY.post("/api/v1/products", novoProduto, null).status()).isEqualTo(401);
        assertThat(Services.INVENTORY.post("/api/v1/products", novoProduto, cliente.token()).status())
                .isEqualTo(403);
        assertThat(Services.INVENTORY.post("/api/v1/products", novoProduto, adminToken).status())
                .isEqualTo(201);
    }

    // ----- apoio -----

    private record Customer(String id, String token) {
    }

    private static String login(String email, String password) {
        ApiClient.Response response = Services.AUTH.post("/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}""".formatted(email, password), null);
        assertThat(response.status())
                .as("login de %s falhou: %s", email, response.body())
                .isEqualTo(200);
        return response.text("accessToken");
    }

    private static Customer registerCustomer(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@exemplo.com";
        String password = "senha-de-teste-" + UUID.randomUUID();

        ApiClient.Response created = Services.AUTH.post("/api/v1/auth/register",
                """
                {"email":"%s","password":"%s","fullName":"Cliente %s"}"""
                        .formatted(email, password, prefix), null);
        assertThat(created.status()).isEqualTo(201);

        return new Customer(created.text("id"), login(email, password));
    }

    private static String createProduct(int quantity) {
        ApiClient.Response created = Services.INVENTORY.post("/api/v1/products",
                """
                {"sku":"E2E-%s","name":"Produto de teste","price":100.00,"initialQuantity":%d}"""
                        .formatted(UUID.randomUUID(), quantity), adminToken);
        assertThat(created.status()).isEqualTo(201);
        return created.text("id");
    }

    private static String orderBody(String productId, int quantity) {
        return """
                {"items":[{"productId":"%s","quantity":%d}]}""".formatted(productId, quantity);
    }

    private static String orderStatus(String orderId, String token) {
        return Services.ORDERS.get("/api/v1/orders/" + orderId, token).text("status");
    }

    private static JsonNode notifications(String orderId, String token) {
        return Services.NOTIFICATIONS
                .get("/api/v1/notifications?orderId=" + orderId, token).json().path("content");
    }

    private static java.util.List<String> notificationTypes(String orderId, String token) {
        return notifications(orderId, token).valueStream()
                .map(n -> n.path("type").asText())
                .toList();
    }
}
