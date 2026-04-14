package com.msa.userapp.modules.shop.common.dto;

import java.util.List;

public record ProductOptionGroupResponse(
        Long id,
        String groupName,
        String groupType,
        int minSelect,
        int maxSelect,
        boolean required,
        List<ProductOptionResponse> options
) {
}
