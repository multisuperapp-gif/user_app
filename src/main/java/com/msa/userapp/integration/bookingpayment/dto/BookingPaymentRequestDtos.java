package com.msa.userapp.integration.bookingpayment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
            BigDecimal searchLongitude,
            Integer requestedProviderCount
    ) {
    }

    public record BookingRequestData(
            Long id,
            String requestCode,
            String requestStatus,
            List<BookingRequestCandidateData> candidates
    ) {
    }

    public record BookingRequestCandidateData(
            Long candidateId,
            String providerEntityType,
            Long providerEntityId,
            String candidateStatus,
            BigDecimal quotedPriceAmount,
            BigDecimal distanceKm,
            LocalDateTime expiresAt
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
            BigDecimal totalAcceptedQuotedPriceAmount,
            BigDecimal totalAcceptedBookingChargeAmount,
            BigDecimal distanceKm,
            Integer requestedProviderCount,
            Integer acceptedProviderCount,
            List<AcceptedProviderData> acceptedProviders,
            Integer pendingProviderCount,
            Long bookingId,
            String bookingCode,
            String bookingStatus,
            String paymentStatus
    ) {
    }

    public record AcceptedProviderData(
            Long candidateId,
            Long providerEntityId,
            String providerName,
            BigDecimal quotedPriceAmount
    ) {
    }

    public record InitiateBookingPaymentRequest(
            Long bookingId,
            Long bookingRequestId
    ) {
    }

    public record CancelBookingRequest(
            String reason
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
