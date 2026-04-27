package com.msa.userapp.modules.order.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.order.dto.CheckoutPreviewRequest;
import com.msa.userapp.modules.order.dto.CheckoutPreviewResponse;
import com.msa.userapp.modules.order.dto.CancelOrderRequest;
import com.msa.userapp.modules.order.dto.OrderDetailResponse;
import com.msa.userapp.modules.order.dto.OrderSummaryResponse;
import com.msa.userapp.modules.order.dto.PlaceOrderRequest;
import com.msa.userapp.modules.order.dto.PlaceOrderResponse;
import com.msa.userapp.modules.order.service.ShopOrdersGatewayService;
import com.msa.userapp.modules.order.service.UserOrderLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class CheckoutController {
    private final UserOrderLifecycleService userOrderLifecycleService;
    private final ShopOrdersGatewayService shopOrdersGatewayService;

    public CheckoutController(
            UserOrderLifecycleService userOrderLifecycleService,
            ShopOrdersGatewayService shopOrdersGatewayService
    ) {
        this.userOrderLifecycleService = userOrderLifecycleService;
        this.shopOrdersGatewayService = shopOrdersGatewayService;
    }

    @GetMapping
    public ApiResponse<List<OrderSummaryResponse>> list(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopOrdersGatewayService.orders(authorizationHeader, userId));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> detail(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    ) {
        return ApiResponse.ok(shopOrdersGatewayService.orderDetail(authorizationHeader, userId, orderId));
    }

    @PostMapping("/checkout-preview")
    public ApiResponse<CheckoutPreviewResponse> preview(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CheckoutPreviewRequest request
    ) {
        return ApiResponse.ok(shopOrdersGatewayService.preview(authorizationHeader, userId, request));
    }

    @PostMapping("/place")
    public ApiResponse<PlaceOrderResponse> place(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PlaceOrderRequest request
    ) {
        return ApiResponse.success("Order created", shopOrdersGatewayService.place(authorizationHeader, userId, request));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Void> cancel(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId,
            @RequestBody(required = false) CancelOrderRequest request
    ) {
        userOrderLifecycleService.cancel(authorizationHeader, userId, orderId, request);
        return ApiResponse.success("Order cancelled successfully");
    }
}
