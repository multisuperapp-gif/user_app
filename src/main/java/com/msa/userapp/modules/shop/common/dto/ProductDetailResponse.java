package com.msa.userapp.modules.shop.common.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailResponse(
        Long productId,
        Long selectedVariantId,
        Long shopId,
        Long shopTypeId,
        Long categoryId,
        String productName,
        String shopName,
        String categoryName,
        String brandName,
        String description,
        String shortDescription,
        String productType,
        String attributesJson,
        BigDecimal avgRating,
        long totalReviews,
        long totalOrders,
        boolean outOfStock,
        List<ProductImageResponse> images,
        List<ProductVariantResponse> variants,
        List<ProductOptionGroupResponse> optionGroups
) {
}
