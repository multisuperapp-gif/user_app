package com.msa.userapp.integration.bookingpayment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class BookingPaymentRequestDtos {
    private BookingPaymentRequestDtos() {
    }

    public record CreateBookingRequest(
            String bookingType,
            String requestMode,
            Long userId,
            Long addressId,
            LocalDateTime scheduledStartAt,
            String targetProviderEntityType,
            Long targetProviderEntityId,
            Long categoryId,
            Long subcategoryId,
            String labourPricingModel,
            BigDecimal priceMinAmount,
            BigDecimal priceMaxAmount,
            BigDecimal searchLatitude,
            BigDecimal searchLongitude
    ) {
    }

    public record BookingRequestData(
            Long id,
            String requestCode,
            String requestStatus
    ) {
    }

    public record UserBookingRequestStatusData(
            Long requestId,
            String requestCode,
            String bookingType,
            String requestStatus,
            Long candidateId,
            String providerEntityType,
            Long providerEntityId,
            String providerName,
            String providerPhone,
            BigDecimal quotedPriceAmount,
            BigDecimal distanceKm,
            Long bookingId,
            String bookingCode,
            String bookingStatus,
            String paymentStatus
    ) {
    }

    public record InitiateBookingPaymentRequest(
            Long bookingId
    ) {
    }

    public record BookingPaymentData(
            Long bookingId,
            String bookingCode,
            String paymentCode,
            BigDecimal amount,
            String currencyCode
    ) {
    }
}
