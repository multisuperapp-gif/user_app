package com.msa.userapp.modules.profile.dto;

import java.math.BigDecimal;

public record UpsertUserAddressRequest(
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
        Boolean isDefault
) {
}
