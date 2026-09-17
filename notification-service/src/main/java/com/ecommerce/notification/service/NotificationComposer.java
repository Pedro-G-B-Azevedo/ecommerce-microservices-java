package com.ecommerce.notification.service;

import com.ecommerce.contracts.StockRejectedEvent;
import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.NotificationChannel;
import com.ecommerce.notification.entity.NotificationType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Monta o conteúdo das mensagens. */
@Component
public class NotificationComposer {

    public Notification orderReceived(UUID orderId, UUID customerId, BigDecimal totalAmount) {
        return compose(orderId, customerId, NotificationType.ORDER_RECEIVED,
                "Recebemos o seu pedido",
                """
                Olá!

                Recebemos o seu pedido %s, no valor de R$ %s.

                Estamos conferindo a disponibilidade dos itens e avisaremos assim que
                estiver tudo certo.""".formatted(orderId, totalAmount));
    }

    public Notification orderConfirmed(UUID orderId, UUID customerId) {
        return compose(orderId, customerId, NotificationType.ORDER_CONFIRMED,
                "Seu pedido foi confirmado",
                """
                Boa notícia!

                O pedido %s foi confirmado e já está sendo preparado para envio.""".formatted(orderId));
    }

    public Notification orderRejected(UUID orderId, UUID customerId, String reason,
                                      List<StockRejectedEvent.Shortfall> shortfalls) {
        StringBuilder body = new StringBuilder("""
                Olá,

                Infelizmente não conseguimos atender o pedido %s.

                Motivo: %s""".formatted(orderId, reason));

        if (shortfalls != null && !shortfalls.isEmpty()) {
            body.append("\n\nItens sem disponibilidade suficiente:");
            for (StockRejectedEvent.Shortfall shortfall : shortfalls) {
                body.append("\n  • produto %s — pedidas %d, disponíveis %d".formatted(
                        shortfall.productId(), shortfall.requested(), shortfall.available()));
            }
        }
        body.append("\n\nNenhuma cobrança foi feita.");

        return compose(orderId, customerId, NotificationType.ORDER_REJECTED,
                "Não conseguimos atender o seu pedido", body.toString());
    }

    private Notification compose(UUID orderId, UUID customerId, NotificationType type,
                                 String subject, String body) {
        return Notification.draft(orderId, customerId, type, NotificationChannel.EMAIL,
                recipientFor(customerId), subject, body);
    }

    /**
     * Endereço fictício derivado do id do cliente.
     *
     * <p>O cadastro de clientes vive no auth-service (etapa 6); até lá, o
     * notification-service não tem de onde tirar um e-mail real.
     */
    private static String recipientFor(UUID customerId) {
        return "cliente-" + customerId + "@exemplo.com";
    }
}
