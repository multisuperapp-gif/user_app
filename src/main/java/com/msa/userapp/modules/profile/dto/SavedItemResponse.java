package com.msa.userapp.modules.profile.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SavedItemResponse(
        Long id,
        String targetType,
        Long targetId,
        String savedKind,
        String title,
        String subtitle,
        String imageObjectKey,
        BigDecimal price,
        BigDecimal rating,
        OffsetDateTime createdAt
) {
}
