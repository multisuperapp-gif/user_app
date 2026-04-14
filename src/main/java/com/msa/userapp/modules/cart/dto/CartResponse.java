package com.msa.userapp.modules.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long cartId,
        Long userId,
        Long shopId,
        String shopName,
        String currencyCode,
        String cartContext,
        int itemCount,
        BigDecimal subtotal,
        List<CartItemResponse> items
) {
}
