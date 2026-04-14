package com.msa.userapp.modules.payment.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.payment.dto.UserPaymentDtos;
import com.msa.userapp.modules.payment.service.UserPaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class UserPaymentController {
    private final UserPaymentService userPaymentService;

    public UserPaymentController(UserPaymentService userPaymentService) {
        this.userPaymentService = userPaymentService;
    }

    @GetMapping("/{paymentCode}")
    public ApiResponse<UserPaymentDtos.PaymentStatusResponse> status(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode
    ) {
        return ApiResponse.ok(userPaymentService.status(authorizationHeader, userId, paymentCode));
    }

    @PostMapping("/{paymentCode}/initiate")
    public ApiResponse<UserPaymentDtos.PaymentInitiateResponse> initiate(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) UserPaymentDtos.PaymentInitiateRequest request
    ) {
        return ApiResponse.ok(userPaymentService.initiate(authorizationHeader, userId, paymentCode, request));
    }

    @PostMapping("/{paymentCode}/verify")
    public ApiResponse<UserPaymentDtos.PaymentStatusResponse> verify(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @Valid @RequestBody UserPaymentDtos.PaymentVerifyRequest request
    ) {
        return ApiResponse.ok(userPaymentService.verify(authorizationHeader, userId, paymentCode, request));
    }

    @PostMapping("/{paymentCode}/failure")
    public ApiResponse<UserPaymentDtos.PaymentStatusResponse> failure(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) UserPaymentDtos.PaymentFailureRequest request
    ) {
        return ApiResponse.ok(userPaymentService.fail(authorizationHeader, userId, paymentCode, request));
    }
}
