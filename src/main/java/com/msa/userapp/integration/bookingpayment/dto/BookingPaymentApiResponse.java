package com.msa.userapp.integration.bookingpayment.dto;

public record BookingPaymentApiResponse<T>(
        boolean success,
        String message,
        String errorCode,
        T data
) {
}
