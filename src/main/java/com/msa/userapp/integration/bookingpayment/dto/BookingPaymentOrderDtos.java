package com.msa.userapp.integration.bookingpayment.dto;

import java.math.BigDecimal;

public final class BookingPaymentOrderDtos {
    private BookingPaymentOrderDtos() {
    }

    public record CancelShopOrderRequest(
            Long orderId,
            Long userId,
            String reason
    ) {
    }

    public record ShopOrderData(
            Long orderId,
            String orderCode,
            Long shopId,
            String orderStatus,
            String paymentStatus,
            String paymentCode,
            String gatewayName,
            String razorpayKeyId,
            String razorpayOrderId,
            BigDecimal subtotalAmount,
            BigDecimal deliveryFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal totalAmount,
            String currencyCode,
            Long amountInPaise,
            String note
    ) {
    }
}
