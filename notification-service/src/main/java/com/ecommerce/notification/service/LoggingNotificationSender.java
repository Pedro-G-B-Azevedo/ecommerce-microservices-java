package com.ecommerce.notification.service;

import com.ecommerce.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Envio simulado: escreve a mensagem no log em vez de entregá-la de verdade.
 *
 * <p>Suficiente para o escopo deste projeto e, principalmente, sem dependência de
 * um serviço externo para rodar em desenvolvimento e nos testes.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("""

                ┌─ NOTIFICAÇÃO ({}) ────────────────────────────
                │ Para:    {}
                │ Assunto: {}
                │ Pedido:  {}
                ├───────────────────────────────────────────────
                {}
                └───────────────────────────────────────────────""",
                notification.getChannel(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getOrderId(),
                notification.getBody());
    }
}
