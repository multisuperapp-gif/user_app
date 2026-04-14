package com.msa.userapp.modules.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        String orderCode,
        Long shopId,
        String shopName,
        String orderStatus,
        String paymentStatus,
        String paymentCode,
        String fulfillmentType,
        String addressLabel,
        String addressLine,
        BigDecimal subtotalAmount,
        BigDecimal deliveryFeeAmount,
        BigDecimal platformFeeAmount,
        BigDecimal totalAmount,
        String currencyCode,
        boolean cancellable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemLineResponse> items,
        List<OrderTimelineEventResponse> timeline,
        RefundSummaryResponse refund
) {
}
