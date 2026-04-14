package com.msa.userapp.modules.profile.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        long unreadCount
) {
}
