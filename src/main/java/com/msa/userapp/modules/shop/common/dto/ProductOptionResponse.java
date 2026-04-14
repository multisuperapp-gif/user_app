package com.msa.userapp.modules.shop.common.dto;

import java.math.BigDecimal;

public record ProductOptionResponse(
        Long id,
        String optionName,
        BigDecimal priceDelta,
        boolean defaultSelected
) {
}
