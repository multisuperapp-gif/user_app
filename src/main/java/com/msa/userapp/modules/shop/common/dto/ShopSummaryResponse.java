package com.msa.userapp.modules.shop.common.dto;

import java.math.BigDecimal;

public record ShopSummaryResponse(
        Long shopId,
        Long shopTypeId,
        String shopName,
        String shopCode,
        String logoObjectKey,
        String coverObjectKey,
        BigDecimal avgRating,
        long totalReviews,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        String deliveryType,
        BigDecimal deliveryRadiusKm,
        BigDecimal minOrderAmount,
        BigDecimal deliveryFee,
        boolean openNow,
        boolean closingSoon,
        boolean acceptsOrders,
        String closesAt
) {
}
