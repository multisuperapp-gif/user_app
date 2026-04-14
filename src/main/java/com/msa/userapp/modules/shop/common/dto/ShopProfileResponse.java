package com.msa.userapp.modules.shop.common.dto;

import java.util.List;

public record ShopProfileResponse(
        ShopSummaryResponse shop,
        List<ShopCategoryResponse> categories,
        PageResponse<ShopProductCardResponse> products
) {
}
