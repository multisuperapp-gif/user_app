package com.msa.userapp.modules.shop.common.dto;

public record ShopCategoryResponse(
        Long id,
        Long parentCategoryId,
        Long shopTypeId,
        String name,
        String normalizedName,
        String themeColor,
        boolean comingSoon,
        String comingSoonMessage,
        String imageObjectKey,
        int sortOrder
) {
}
