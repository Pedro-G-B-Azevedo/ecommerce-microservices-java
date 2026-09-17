package com.ecommerce.notification.entity;

/**
 * Por onde a notificação sai.
 *
 * <p>Neste projeto só existe {@link #EMAIL}, e o envio é simulado em log. O enum
 * existe para que acrescentar SMS ou push não exija mudar o schema.
 */
public enum NotificationChannel {

    EMAIL
}
