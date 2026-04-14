package com.msa.userapp.modules.shop.common.dto;

import java.math.BigDecimal;

public record ShopProductCardResponse(
        Long productId,
        Long variantId,
        Long shopId,
        Long shopTypeId,
        Long categoryId,
        String productName,
        String shopName,
        String categoryName,
        String brandName,
        String shortDescription,
        String productType,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        BigDecimal avgRating,
        long totalReviews,
        long totalOrders,
        String inventoryStatus,
        boolean outOfStock,
        int promotionScore,
        String imageObjectKey
) {
}
