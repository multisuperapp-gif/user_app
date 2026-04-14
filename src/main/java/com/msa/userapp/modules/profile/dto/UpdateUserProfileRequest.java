package com.msa.userapp.modules.profile.dto;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
        String fullName,
        String gender,
        LocalDate dob,
        String languageCode
) {
}
