package com.ecommerce.notification.repository;

import com.ecommerce.notification.entity.Notification;
import com.ecommerce.notification.entity.NotificationStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> hasOrderId(UUID orderId) {
        return (root, query, cb) -> cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<Notification> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Notification> hasStatus(NotificationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
