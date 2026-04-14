package com.msa.userapp.modules.profile.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.profile.dto.PushTokenDeactivateRequest;
import com.msa.userapp.modules.profile.dto.PushTokenRegisterRequest;
import com.msa.userapp.modules.profile.dto.PushTokenResponse;
import com.msa.userapp.modules.profile.service.PushTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/push-tokens")
public class PushTokenController {
    private final PushTokenService pushTokenService;

    public PushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @PostMapping
    public ApiResponse<PushTokenResponse> register(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PushTokenRegisterRequest request
    ) {
        return ApiResponse.success("Push token registered", pushTokenService.register(userId, request));
    }

    @PatchMapping("/deactivate")
    public ApiResponse<Void> deactivate(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PushTokenDeactivateRequest request
    ) {
        pushTokenService.deactivate(userId, request);
        return ApiResponse.success("Push token deactivated");
    }
}
