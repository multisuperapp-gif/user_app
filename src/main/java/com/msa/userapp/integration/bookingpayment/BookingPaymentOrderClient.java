package com.msa.userapp.integration.bookingpayment;

import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentOrderDtos.CancelShopOrderRequest;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentOrderDtos.ShopOrderData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "bookingPaymentOrderClient", url = "${app.integrations.booking-payment-url}")
public interface BookingPaymentOrderClient {
    @PostMapping("/shop-orders/cancel")
    BookingPaymentApiResponse<ShopOrderData> cancelByUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody CancelShopOrderRequest request
    );
}
