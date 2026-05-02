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
            List<LabourCategoryPricingResponse> categoryPricings,
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
            BigDecimal radiusKm,
            BigDecimal workLatitude,
            BigDecimal workLongitude,
            boolean onlineStatus,
            boolean availableNow,
            String availabilityStatus,
            int activeBookingCount,
            String skillsSummary
    ) {
    }

    public record LabourCategoryPricingResponse(
            Long categoryId,
            String categoryName,
            BigDecimal halfDayRate,
            BigDecimal fullDayRate
    ) {
    }

    public record LabourProfileResponse(
            LabourProfileCardResponse profile,
            List<String> skills
    ) {
    }

    public record LabourBookingPolicyResponse(
            BigDecimal bookingChargePercent,
            String currencyCode,
            int maxGroupLabourCount
    ) {
    }

    public record DirectLabourBookingRequest(
            Long labourId,
            Long categoryId,
            Long addressId,
            String bookingPeriod
    ) {
    }

    public record CancelLabourBookingRequest(
            String reason
    ) {
    }

    public record DirectLabourBookingResponse(
            Long requestId,
            String requestCode,
            String requestStatus,
            BigDecimal quotedPriceAmount,
            String currencyCode,
            String labourName
    ) {
    }

    public record LabourBookingRequestStatusResponse(
            Long requestId,
            String requestCode,
            String requestStatus,
            String providerName,
            String providerPhone,
            BigDecimal quotedPriceAmount,
            BigDecimal totalAcceptedQuotedPriceAmount,
            BigDecimal totalAcceptedBookingChargeAmount,
            BigDecimal distanceKm,
            int requestedProviderCount,
            int acceptedProviderCount,
            List<AcceptedProviderResponse> acceptedProviders,
            int pendingProviderCount,
            Long bookingId,
            String bookingCode,
            String bookingStatus,
            String paymentStatus,
            boolean canMakePayment
    ) {
    }

    public record AcceptedProviderResponse(
            Long candidateId,
            Long providerEntityId,
            String providerName,
            BigDecimal quotedPriceAmount
    ) {
    }

    public record LabourBookingPaymentResponse(
            Long bookingId,
            String bookingCode,
            String paymentCode,
            BigDecimal amount,
            String currencyCode
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
            BigDecimal bookingChargePercent,
            BigDecimal estimatedLabourAmount,
            BigDecimal platformAmountDue,
            String currencyCode,
            String requestStatus
    ) {
    }
}
