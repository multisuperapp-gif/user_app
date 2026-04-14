package com.msa.userapp.modules.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long orderId,
        String orderCode,
        Long shopId,
        String shopName,
        String primaryItemName,
        String primaryImageFileId,
        Integer itemCount,
        String orderStatus,
        String paymentStatus,
        BigDecimal totalAmount,
        String currencyCode,
        boolean cancellable,
        boolean refundPresent,
        String latestRefundStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
