package com.ecommerce.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Registro de uma notificação.
 *
 * <p>A entidade guarda o conteúdo enviado, e não apenas o fato do envio: sem o
 * corpo gravado, não há como auditar depois o que o cliente realmente recebeu.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
        // Exigido pelo JPA.
    }

    private Notification(UUID orderId, UUID customerId, NotificationType type,
                         NotificationChannel channel, String recipient, String subject, String body) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.type = type;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public static Notification draft(UUID orderId, UUID customerId, NotificationType type,
                                     NotificationChannel channel, String recipient,
                                     String subject, String body) {
        return new Notification(orderId, customerId, type, channel, recipient, subject, body);
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.failureReason = null;
    }

    /**
     * Marca a falha sem lançar exceção: a notificação é registrada como
     * {@code FAILED} para que o problema fique visível no histórico, em vez de
     * sumir num log.
     */
    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = truncate(reason);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "Motivo não informado";
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
