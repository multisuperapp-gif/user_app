package com.msa.userapp.modules.service.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import com.msa.userapp.modules.service.service.ServiceBookingRequestService;
import com.msa.userapp.modules.service.service.ServiceBookingService;
import com.msa.userapp.modules.service.service.ServiceDiscoveryQueryService;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceController {
    private final ServiceDiscoveryQueryService serviceDiscoveryQueryService;
    private final ServiceBookingService serviceBookingService;
    private final ServiceBookingRequestService serviceBookingRequestService;

    public ServiceController(
            ServiceDiscoveryQueryService serviceDiscoveryQueryService,
            ServiceBookingService serviceBookingService,
            ServiceBookingRequestService serviceBookingRequestService
    ) {
        this.serviceDiscoveryQueryService = serviceDiscoveryQueryService;
        this.serviceBookingService = serviceBookingService;
        this.serviceBookingRequestService = serviceBookingRequestService;
    }

    @GetMapping("/api/v1/public/service/landing")
    public ApiResponse<ServiceApiDtos.ServiceLandingResponse> landing(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(serviceDiscoveryQueryService.landing(
                userId,
                categoryId,
                subcategoryId,
                city,
                latitude,
                longitude,
                page,
                size
        ));
    }

    @GetMapping("/api/v1/public/service/categories")
    public ApiResponse<List<ServiceApiDtos.ServiceCategoryResponse>> categories() {
        return ApiResponse.ok(serviceDiscoveryQueryService.categories());
    }

    @GetMapping("/api/v1/public/service/providers")
    public ApiResponse<PageResponse<ServiceApiDtos.ServiceProviderCardResponse>> providers(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(serviceDiscoveryQueryService.providers(userId, categoryId, subcategoryId, search, page, size));
    }

    @GetMapping("/api/v1/public/service/providers/{providerId}")
    public ApiResponse<ServiceApiDtos.ServiceProviderProfileResponse> providerProfile(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long providerId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId
    ) {
        return ApiResponse.ok(serviceDiscoveryQueryService.providerProfile(userId, providerId, categoryId, subcategoryId));
    }

    @PostMapping("/api/v1/service/bookings/direct")
    public ApiResponse<ServiceApiDtos.DirectServiceBookingResponse> directBooking(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ServiceApiDtos.DirectServiceBookingRequest request
    ) {
        return ApiResponse.ok(serviceBookingRequestService.createDirectBookingRequest(authorizationHeader, userId, request));
    }

    @GetMapping("/api/v1/service/booking-requests/{requestId}")
    public ApiResponse<ServiceApiDtos.ServiceBookingRequestStatusResponse> requestStatus(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId
    ) {
        return ApiResponse.ok(serviceBookingRequestService.fetchRequestStatus(authorizationHeader, userId, requestId));
    }

    @PostMapping("/api/v1/service/booking-requests/{requestId}/payment/initiate")
    public ApiResponse<ServiceApiDtos.ServiceBookingPaymentResponse> initiateRequestPayment(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId
    ) {
        return ApiResponse.ok(serviceBookingRequestService.initiatePayment(authorizationHeader, userId, requestId));
    }
}
