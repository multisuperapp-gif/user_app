package com.msa.userapp.modules.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CartUpdateItemRequest(
        @NotNull @Min(1) Integer quantity,
        List<Long> optionIds,
        String cookingRequest
) {
}
