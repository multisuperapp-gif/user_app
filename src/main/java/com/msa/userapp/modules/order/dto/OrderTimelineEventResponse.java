package com.msa.userapp.modules.order.dto;

import java.time.LocalDateTime;

public record OrderTimelineEventResponse(
        String oldStatus,
        String newStatus,
        String reason,
        LocalDateTime changedAt
) {
}
