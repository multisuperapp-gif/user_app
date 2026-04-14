package com.msa.userapp.modules.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundSummaryResponse(
        String refundCode,
        String refundStatus,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        String reason,
        LocalDateTime initiatedAt,
        LocalDateTime completedAt
) {
}
