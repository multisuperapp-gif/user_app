package com.msa.userapp.integration.bookingpayment;

import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentRequestDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "bookingPaymentRequestClient", url = "${app.integrations.booking-payment-url}")
public interface BookingPaymentRequestClient {
    @PostMapping("/booking-requests")
    BookingPaymentApiResponse<BookingPaymentRequestDtos.BookingRequestData> create(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody BookingPaymentRequestDtos.CreateBookingRequest request
    );

    @GetMapping("/booking-requests/{requestId}")
    BookingPaymentApiResponse<BookingPaymentRequestDtos.UserBookingRequestStatusData> status(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId
    );

    @PostMapping("/booking-requests/{requestId}/cancel")
    BookingPaymentApiResponse<Void> cancel(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long requestId,
            @RequestBody BookingPaymentRequestDtos.CancelBookingRequest request
    );

    @PostMapping("/booking-payments/initiate")
    BookingPaymentApiResponse<BookingPaymentRequestDtos.BookingPaymentData> initiateBookingPayment(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody BookingPaymentRequestDtos.InitiateBookingPaymentRequest request
    );
}
