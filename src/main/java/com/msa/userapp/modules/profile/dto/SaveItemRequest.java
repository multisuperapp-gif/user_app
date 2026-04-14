package com.msa.userapp.modules.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveItemRequest(
        @NotBlank String targetType,
        @NotNull Long targetId,
        @NotBlank String savedKind
) {
}
