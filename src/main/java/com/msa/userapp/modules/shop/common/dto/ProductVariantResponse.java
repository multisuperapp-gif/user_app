package com.msa.userapp.modules.shop.common.dto;

import java.math.BigDecimal;

public record ProductVariantResponse(
        Long id,
        String variantName,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        boolean defaultVariant,
        boolean active,
        String attributesJson,
        String inventoryStatus,
        boolean outOfStock
) {
}
