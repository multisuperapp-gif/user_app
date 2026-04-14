package com.msa.userapp.modules.labour.dto;

import com.msa.userapp.modules.shop.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;

public final class LabourApiDtos {
    private LabourApiDtos() {
    }

    public record LabourLandingResponse(
            List<LabourCategoryResponse> categories,
            PageResponse<LabourProfileCardResponse> profiles
    ) {
    }

    public record LabourCategoryResponse(
            Long id,
            String name,
            String normalizedName
    ) {
    }

    public record LabourProfileCardResponse(
            Long labourId,
            Long categoryId,
            String categoryName,
            String fullName,
            String photoObjectKey,
            String maskedPhone,
            int experienceYears,
            BigDecimal hourlyRate,
            BigDecimal halfDayRate,
            BigDecimal fullDayRate,
            BigDecimal rating,
            long completedJobs,
            BigDecimal distanceKm,
            boolean availableToday,
            String skillsSummary
    ) {
    }

    public record LabourProfileResponse(
            LabourProfileCardResponse profile,
            List<String> skills
    ) {
    }

    public record DirectLabourBookingRequest(
            Long labourId,
            Long categoryId,
            Long addressId,
            String bookingPeriod
    ) {
    }

    public record DirectLabourBookingResponse(
            Long bookingId,
            String bookingCode,
            String bookingStatus,
            String paymentStatus,
            Long paymentId,
            String paymentCode,
            BigDecimal payableAmount,
            String currencyCode,
            String labourName
    ) {
    }

    public record GroupLabourBookingRequest(
            Long categoryId,
            Long addressId,
            String bookingPeriod,
            BigDecimal maxPrice,
            Integer labourCount
    ) {
    }

    public record GroupLabourBookingResponse(
            Long requestId,
            String requestCode,
            int availableCandidates,
            int requestedCount,
            BigDecimal platformAmountDue,
            String currencyCode,
            String requestStatus
    ) {
    }
}
