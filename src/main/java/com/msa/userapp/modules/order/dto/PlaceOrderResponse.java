package com.msa.userapp.modules.order.dto;

import java.math.BigDecimal;

public record PlaceOrderResponse(
        Long orderId,
        String orderCode,
        String orderStatus,
        String paymentStatus,
        Long paymentId,
        String paymentCode,
        BigDecimal totalAmount,
        String currencyCode,
        String nextAction
) {
}
