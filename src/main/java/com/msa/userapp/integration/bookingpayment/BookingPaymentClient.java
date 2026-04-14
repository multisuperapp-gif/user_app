package com.msa.userapp.integration.bookingpayment;

import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos.PaymentFailureRequest;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos.PaymentInitiateRequest;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos.PaymentInitiateResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos.PaymentStatusResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos.PaymentVerifyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "bookingPaymentClient", url = "${app.integrations.booking-payment-url}")
public interface BookingPaymentClient {
    @GetMapping("/api/v1/payments/{paymentCode}")
    BookingPaymentApiResponse<PaymentStatusResponse> status(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode
    );

    @PostMapping("/api/v1/payments/{paymentCode}/initiate")
    BookingPaymentApiResponse<PaymentInitiateResponse> initiate(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) PaymentInitiateRequest request
    );

    @PostMapping("/api/v1/payments/{paymentCode}/verify")
    BookingPaymentApiResponse<PaymentStatusResponse> verify(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody PaymentVerifyRequest request
    );

    @PostMapping("/api/v1/payments/{paymentCode}/failure")
    BookingPaymentApiResponse<PaymentStatusResponse> failure(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) PaymentFailureRequest request
    );
}
