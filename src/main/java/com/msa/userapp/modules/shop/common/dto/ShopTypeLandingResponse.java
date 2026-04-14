package com.msa.userapp.modules.shop.common.dto;

import java.util.List;

public record ShopTypeLandingResponse(
        ShopTypeResponse shopType,
        List<ShopCategoryResponse> categories,
        PageResponse<ShopProductCardResponse> products,
        PageResponse<ShopSummaryResponse> shops
) {
}
