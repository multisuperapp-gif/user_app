package com.msa.userapp.modules.profile.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.profile.dto.NotificationListResponse;
import com.msa.userapp.modules.profile.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return ApiResponse.ok(notificationService.list(userId, page, size, unreadOnly));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.success("Notification marked as read");
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.success("All notifications marked as read");
    }
}
