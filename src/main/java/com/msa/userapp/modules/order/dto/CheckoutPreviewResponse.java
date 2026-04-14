package com.msa.userapp.modules.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(
        Long cartId,
        Long shopId,
        String shopName,
        Long addressId,
        String addressLabel,
        String addressLine,
        String fulfillmentType,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal platformFee,
        BigDecimal totalAmount,
        String currencyCode,
        boolean shopOpen,
        boolean closingSoon,
        boolean acceptsOrders,
        boolean canPlaceOrder,
        List<String> issues
) {
}
