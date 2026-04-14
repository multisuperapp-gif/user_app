package com.msa.userapp.modules.shop.common.dto;

import java.util.List;

public record HomeBootstrapResponse(
        List<ShopTypeResponse> shopTypes,
        PageResponse<ShopProductCardResponse> topProducts
) {
}
