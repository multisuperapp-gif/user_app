package com.msa.userapp.modules.order.dto;

import java.math.BigDecimal;

public record OrderItemLineResponse(
        Long orderItemId,
        Long productId,
        Long variantId,
        String productName,
        String variantName,
        String imageFileId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
