package com.msa.userapp.modules.profile.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.profile.dto.UpdateUserProfileRequest;
import com.msa.userapp.modules.profile.dto.UserProfileResponse;
import com.msa.userapp.modules.profile.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> profile(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(profileService.profile(userId));
    }

    @PatchMapping
    public ApiResponse<UserProfileResponse> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody UpdateUserProfileRequest request
    ) {
        return ApiResponse.ok(profileService.updateProfile(userId, request));
    }
}
