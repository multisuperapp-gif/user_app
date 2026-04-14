package com.msa.userapp.modules.shop.common.dto;

public record ShopTypeResponse(
        Long id,
        String name,
        String normalizedName,
        String themeColor,
        boolean comingSoon,
        String comingSoonMessage,
        String iconObjectKey,
        String bannerObjectKey,
        int sortOrder
) {
}
