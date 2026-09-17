package com.ecommerce.notification.dto;

import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.NotificationChannel;
import com.ecommerce.notification.entity.NotificationStatus;
import com.ecommerce.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Notificação registrada")
public record NotificationResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        NotificationType type,
        NotificationChannel channel,
        String recipient,
        String subject,
        String body,
        NotificationStatus status,
        String failureReason,
        Instant createdAt,
        Instant sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrderId(),
                notification.getCustomerId(),
                notification.getType(),
                notification.getChannel(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getBody(),
                notification.getStatus(),
                notification.getFailureReason(),
                notification.getCreatedAt(),
                notification.getSentAt());
    }
}
