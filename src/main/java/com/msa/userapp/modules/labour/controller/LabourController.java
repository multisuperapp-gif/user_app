package com.msa.userapp.modules.labour.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import com.msa.userapp.modules.labour.service.LabourBookingRequestService;
import com.msa.userapp.modules.labour.service.LabourBookingService;
import com.msa.userapp.modules.labour.service.LabourQueryService;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LabourController {
    private final LabourQueryService labourQueryService;
    private final LabourBookingService labourBookingService;
    private final LabourBookingRequestService labourBookingRequestService;

    public LabourController(
            LabourQueryService labourQueryService,
            LabourBookingService labourBookingService,
            LabourBookingRequestService labourBookingRequestService
    ) {
        this.labourQueryService = labourQueryService;
        this.labourBookingService = labourBookingService;
        this.labourBookingRequestService = labourBookingRequestService;
    }

    @GetMapping("/api/v1/public/labour/landing")
    public ApiResponse<LabourApiDtos.LabourLandingResponse> landing(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(labourQueryService.landing(userId, categoryId, city, latitude, longitude, page, size));
    }

    @GetMapping("/api/v1/public/labour/categories")
    public ApiResponse<List<LabourApiDtos.LabourCategoryResponse>> categories() {
        return ApiResponse.ok(labourQueryService.categories());
    }

    @GetMapping("/api/v1/public/labour/booking-policy")
    public ApiResponse<LabourApiDtos.LabourBookingPolicyResponse> bookingPolicy() {
        return ApiResponse.ok(labourBookingRequestService.bookingPolicy());
    }

    @GetMapping("/api/v1/public/labour/profiles")
    public ApiResponse<PageResponse<LabourApiDtos.LabourProfileCardResponse>> profiles(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(labourQueryService.profiles(userId, categoryId, search, page, size));
    }

    @GetMapping("/api/v1/public/labour/profiles/{labourId}")
    public ApiResponse<LabourApiDtos.LabourProfileResponse> profile(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long labourId
    ) {
        return ApiResponse.ok(labourQueryService.profile(userId, labourId));
    }

    @PostMapping("/api/v1/labour/bookings/direct")
    public ApiResponse<LabourApiDtos.DirectLabourBookingResponse> directBooking(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LabourApiDtos.DirectLabourBookingRequest request
    ) {
        return ApiResponse.ok(labourBookingRequestService.createDirectBookingRequest(authorizationHeader, userId, request));
    }

    @GetMapping("/api/v1/labour/booking-requests/{requestId}")
    public ApiResponse<LabourApiDtos.LabourBookingRequestStatusResponse> requestStatus(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId
    ) {
        return ApiResponse.ok(labourBookingRequestService.fetchRequestStatus(authorizationHeader, userId, requestId));
    }

    @PostMapping("/api/v1/labour/booking-requests/{requestId}/payment/initiate")
    public ApiResponse<LabourApiDtos.LabourBookingPaymentResponse> initiateRequestPayment(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId
    ) {
        return ApiResponse.ok(labourBookingRequestService.initiatePayment(authorizationHeader, userId, requestId));
    }

    @PostMapping("/api/v1/labour/booking-requests/{requestId}/cancel")
    public ApiResponse<Void> cancelRequest(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId,
            @RequestBody(required = false) LabourApiDtos.CancelLabourBookingRequest request
    ) {
        labourBookingRequestService.cancelRequest(
                authorizationHeader,
                userId,
                requestId,
                request == null ? null : request.reason()
        );
        return ApiResponse.success("Labour booking request cancelled successfully");
    }

    @PostMapping("/api/v1/labour/bookings/group-request")
    public ApiResponse<LabourApiDtos.GroupLabourBookingResponse> groupBooking(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LabourApiDtos.GroupLabourBookingRequest request
    ) {
        return ApiResponse.ok(labourBookingRequestService.createGroupBookingRequest(authorizationHeader, userId, request));
    }
}
