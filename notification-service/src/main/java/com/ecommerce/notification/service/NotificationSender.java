package com.ecommerce.notification.service;

import com.ecommerce.notification.entity.Notification;

/**
 * Entrega a notificação ao cliente.
 *
 * <p>É uma interface para que trocar o log por um provedor real de e-mail não
 * exija tocar no serviço nem nos listeners.
 */
public interface NotificationSender {

    void send(Notification notification);
}
