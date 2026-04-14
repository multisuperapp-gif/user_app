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
            BigDecimal distanceKm
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
            Long bookingId,
            String bookingCode,
            String bookingStatus,
            String paymentStatus,
            Long paymentId,
            String paymentCode,
            BigDecimal payableAmount,
            String currencyCode,
            String providerName,
            String serviceName
    ) {
    }
}
