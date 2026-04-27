package com.msa.userapp.modules.profile.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        Long userId,
        String publicUserId,
        String phone,
        String fullName,
        String profilePhotoDataUri,
        String profilePhotoObjectKey,
        String gender,
        LocalDate dob,
        String languageCode
) {
}
