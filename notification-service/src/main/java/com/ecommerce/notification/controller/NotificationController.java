package com.ecommerce.notification.controller;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.dto.PageResponse;
import com.ecommerce.notification.entity.NotificationStatus;
import com.ecommerce.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificações", description = "Histórico do que foi comunicado aos clientes")
@SecurityRequirement(name = "bearer-jwt")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Lista notificações",
            description = "Somente leitura: as notificações nascem do consumo de eventos, "
                    + "nunca de uma chamada à API. Um cliente enxerga apenas as próprias.")
    public PageResponse<NotificationResponse> search(
            @Parameter(description = "Filtra pelo pedido")
            @RequestParam(required = false) UUID orderId,

            @Parameter(description = "Filtra pelo cliente")
            @RequestParam(required = false) UUID customerId,

            @Parameter(description = "Filtra por situação do envio")
            @RequestParam(required = false) NotificationStatus status,

            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,

            @AuthenticationPrincipal Jwt jwt) {

        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        boolean isAdmin = roles != null && roles.contains("ADMIN");
        return notificationService.search(orderId, customerId, status, pageable,
                UUID.fromString(jwt.getSubject()), isAdmin);
    }
}
