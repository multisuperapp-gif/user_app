package com.msa.userapp.modules.profile.dto;

import java.time.OffsetDateTime;

public record PushTokenResponse(
        Long id,
        Long userDeviceId,
        String platform,
        String pushProvider,
        String pushToken,
        boolean active,
        OffsetDateTime lastSeenAt
) {
}
