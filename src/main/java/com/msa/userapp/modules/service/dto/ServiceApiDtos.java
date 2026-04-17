package com.msa.userapp.modules.service.dto;

import com.msa.userapp.modules.shop.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;

public final class ServiceApiDtos {
    private ServiceApiDtos() {
    }

    public record ServiceLandingResponse(
            List<ServiceCategoryResponse> categories,
            PageResponse<ServiceProviderCardResponse> providers
    ) {
    }

    public record ServiceCategoryResponse(
            Long id,
            String name,
            List<ServiceSubcategoryResponse> subcategories
    ) {
    }

    public record ServiceSubcategoryResponse(
            Long id,
            Long categoryId,
            String name
    ) {
    }

    public record ServiceProviderCardResponse(
            Long providerId,
            Long categoryId,
            Long subcategoryId,
            String categoryName,
            String subcategoryName,
            String providerName,
            String serviceName,
            String photoObjectKey,
            String maskedPhone,
            BigDecimal visitingCharge,
            BigDecimal rating,
            long completedJobs,
            int availableServiceMen,
            BigDecimal distanceKm,
            boolean onlineStatus,
            boolean availableNow,
            String availabilityStatus,
            int activeBookingCount,
            int remainingServiceMen
    ) {
    }

    public record ServiceProviderProfileResponse(
            ServiceProviderCardResponse provider,
            List<String> serviceItems
    ) {
    }

    public record DirectServiceBookingRequest(
            Long providerId,
            Long categoryId,
            Long subcategoryId,
            Long addressId
    ) {
    }

    public record DirectServiceBookingResponse(
            Long requestId,
            String requestCode,
            String requestStatus,
            BigDecimal quotedPriceAmount,
            String currencyCode,
            String providerName,
            String serviceName
    ) {
    }

    public record ServiceBookingRequestStatusResponse(
            Long requestId,
            String requestCode,
            String requestStatus,
            String providerName,
            String providerPhone,
            BigDecimal quotedPriceAmount,
            BigDecimal distanceKm,
            Long bookingId,
            String bookingCode,
            String bookingStatus,
            String paymentStatus,
            boolean canMakePayment
    ) {
    }

    public record ServiceBookingPaymentResponse(
            Long bookingId,
            String bookingCode,
            String paymentCode,
            BigDecimal amount,
            String currencyCode
    ) {
    }
}
