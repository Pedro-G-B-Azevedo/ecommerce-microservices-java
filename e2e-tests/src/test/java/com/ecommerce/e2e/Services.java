package com.ecommerce.e2e;

/**
 * Endereços dos serviços sob teste.
 *
 * <p>Vêm de variáveis de ambiente para que o mesmo teste sirva tanto para a pilha do
 * docker compose quanto para serviços rodando pela IDE.
 */
final class Services {

    static final ApiClient AUTH = new ApiClient(env("AUTH_URL", "http://localhost:8081"));
    static final ApiClient ORDERS = new ApiClient(env("ORDERS_URL", "http://localhost:8082"));
    static final ApiClient INVENTORY = new ApiClient(env("INVENTORY_URL", "http://localhost:8083"));
    static final ApiClient NOTIFICATIONS = new ApiClient(env("NOTIFICATIONS_URL", "http://localhost:8084"));

    static final String ADMIN_EMAIL = env("E2E_ADMIN_EMAIL", "admin@ecommerce.local");
    static final String ADMIN_PASSWORD = env("E2E_ADMIN_PASSWORD", "troque-esta-senha-admin");

    private Services() {
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
