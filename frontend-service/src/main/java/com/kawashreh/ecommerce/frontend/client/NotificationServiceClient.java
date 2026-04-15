package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.NotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Notification Service.
 * Uses API Gateway for notification functionality.
 */
@FeignClient(name = "notification-service-UI-client", url = "${api.gateway.base-url}/api/v1/notifications")
public interface NotificationServiceClient {

    @GetMapping("/user/{userId}")
    List<NotificationDto> getNotifications(@PathVariable("userId") UUID userId);

    @GetMapping("/user/{userId}/unread")
    List<NotificationDto> getUnreadNotifications(@PathVariable("userId") UUID userId);

    @PostMapping("/{notificationId}/read")
    NotificationDto markAsRead(@PathVariable("notificationId") UUID notificationId);

    @PostMapping("/user/{userId}/mark-all-read")
    void markAllAsRead(@PathVariable("userId") UUID userId);
}