package com.msa.userapp.modules.profile.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UserAddressResponse(
        Long id,
        String label,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        Long stateId,
        String state,
        Long countryId,
        String country,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
