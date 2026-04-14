package com.msa.userapp.modules.labour.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
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

    public LabourController(
            LabourQueryService labourQueryService,
            LabourBookingService labourBookingService
    ) {
        this.labourQueryService = labourQueryService;
        this.labourBookingService = labourBookingService;
    }

    @GetMapping("/api/v1/public/labour/landing")
    public ApiResponse<LabourApiDtos.LabourLandingResponse> landing(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(labourQueryService.landing(userId, categoryId, page, size));
    }

    @GetMapping("/api/v1/public/labour/categories")
    public ApiResponse<List<LabourApiDtos.LabourCategoryResponse>> categories() {
        return ApiResponse.ok(labourQueryService.categories());
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
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LabourApiDtos.DirectLabourBookingRequest request
    ) {
        return ApiResponse.ok(labourBookingService.createDirectBooking(userId, request));
    }

    @PostMapping("/api/v1/labour/bookings/group-request")
    public ApiResponse<LabourApiDtos.GroupLabourBookingResponse> groupBooking(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody LabourApiDtos.GroupLabourBookingRequest request
    ) {
        return ApiResponse.ok(labourBookingService.createGroupRequest(userId, request));
    }
}
