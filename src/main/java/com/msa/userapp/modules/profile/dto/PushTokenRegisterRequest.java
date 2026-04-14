package com.msa.userapp.modules.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record PushTokenRegisterRequest(
        Long userDeviceId,
        String deviceToken,
        String appVersion,
        String osVersion,
        @NotBlank(message = "platform is required")
        String platform,
        @NotBlank(message = "pushProvider is required")
        String pushProvider,
        @NotBlank(message = "pushToken is required")
        String pushToken
) {
}
