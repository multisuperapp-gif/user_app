package com.msa.userapp.modules.profile.dto;

import java.time.OffsetDateTime;

public record NotificationResponse(
        Long id,
        String channel,
        String notificationType,
        String title,
        String body,
        String payloadJson,
        String status,
        OffsetDateTime sentAt,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}
