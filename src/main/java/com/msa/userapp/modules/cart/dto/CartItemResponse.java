package com.msa.userapp.modules.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartItemResponse(
        Long id,
        String lineKey,
        Long productId,
        Long variantId,
        String productName,
        String variantName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String imageObjectKey,
        List<String> selectedOptions,
        String cookingRequest
) {
}
