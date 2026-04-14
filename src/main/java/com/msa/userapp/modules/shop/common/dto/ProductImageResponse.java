package com.msa.userapp.modules.shop.common.dto;

public record ProductImageResponse(
        Long id,
        String objectKey,
        String imageRole,
        int sortOrder,
        boolean primary
) {
}
