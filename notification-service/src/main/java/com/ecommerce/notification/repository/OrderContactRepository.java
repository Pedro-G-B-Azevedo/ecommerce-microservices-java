package com.ecommerce.notification.repository;

import com.ecommerce.notification.entity.OrderContact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderContactRepository extends JpaRepository<OrderContact, UUID> {
}
