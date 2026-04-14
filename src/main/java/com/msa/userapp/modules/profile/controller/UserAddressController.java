package com.msa.userapp.modules.profile.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.profile.dto.UpsertUserAddressRequest;
import com.msa.userapp.modules.profile.dto.UserAddressResponse;
import com.msa.userapp.modules.profile.service.ProfileService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/addresses")
public class UserAddressController {
    private final ProfileService profileService;

    public UserAddressController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<List<UserAddressResponse>> addresses(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(profileService.addresses(userId));
    }

    @PostMapping
    public ApiResponse<UserAddressResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody UpsertUserAddressRequest request
    ) {
        return ApiResponse.ok(profileService.createAddress(userId, request));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<UserAddressResponse> update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long addressId,
            @RequestBody UpsertUserAddressRequest request
    ) {
        return ApiResponse.ok(profileService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long addressId
    ) {
        profileService.deleteAddress(userId, addressId);
        return ApiResponse.success("Address deleted");
    }

    @PostMapping("/{addressId}/default")
    public ApiResponse<UserAddressResponse> setDefault(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long addressId
    ) {
        return ApiResponse.ok(profileService.setDefaultAddress(userId, addressId));
    }
}
