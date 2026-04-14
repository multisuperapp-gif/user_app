package com.msa.userapp.modules.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record PushTokenDeactivateRequest(
        @NotBlank(message = "pushToken is required")
        String pushToken
) {
}
