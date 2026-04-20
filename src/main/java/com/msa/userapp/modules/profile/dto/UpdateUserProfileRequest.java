package com.msa.userapp.modules.profile.dto;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
        String fullName,
        String profilePhotoDataUri,
        String gender,
        LocalDate dob,
        String languageCode
) {
}
