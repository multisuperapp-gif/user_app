package com.msa.userapp.modules.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CartAddItemRequest(
        @NotNull Long productId,
        Long variantId,
        @NotNull @Min(1) Integer quantity,
        List<Long> optionIds,
        String cookingRequest
) {
}
