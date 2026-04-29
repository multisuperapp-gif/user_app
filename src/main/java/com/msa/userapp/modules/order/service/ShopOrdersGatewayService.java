package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.integration.shoporders.ShopOrdersClient;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersApiResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos;
import com.msa.userapp.modules.cart.dto.CartAddItemRequest;
import com.msa.userapp.modules.cart.dto.CartItemResponse;
import com.msa.userapp.modules.cart.dto.CartResponse;
import com.msa.userapp.modules.cart.dto.CartUpdateItemRequest;
import com.msa.userapp.modules.order.dto.CheckoutPreviewRequest;
import com.msa.userapp.modules.order.dto.CheckoutPreviewResponse;
import com.msa.userapp.modules.order.dto.OrderDetailResponse;
import com.msa.userapp.modules.order.dto.OrderItemLineResponse;
import com.msa.userapp.modules.order.dto.OrderSummaryResponse;
import com.msa.userapp.modules.order.dto.OrderTimelineEventResponse;
import com.msa.userapp.modules.order.dto.PlaceOrderRequest;
import com.msa.userapp.modules.order.dto.PlaceOrderResponse;
import com.msa.userapp.modules.order.dto.RefundSummaryResponse;
import com.msa.userapp.modules.payment.dto.UserPaymentDtos;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShopOrdersGatewayService {
    private final ShopOrdersClient shopOrdersClient;

    public ShopOrdersGatewayService(ShopOrdersClient shopOrdersClient) {
        this.shopOrdersClient = shopOrdersClient;
    }

    public CartResponse cart(String authorizationHeader, Long userId) {
        return mapCart(requireSuccess(call(() -> shopOrdersClient.cart(authorizationHeader, userId))));
    }

    public List<OrderSummaryResponse> orders(String authorizationHeader, Long userId) {
        List<ShopOrdersDtos.ConsumerOrderSummaryData> data = requireSuccess(call(() -> shopOrdersClient.orders(authorizationHeader, userId)));
        return data == null ? List.of() : data.stream()
                .map(order -> new OrderSummaryResponse(
                        order.orderId(),
                        order.orderCode(),
                        order.shopId(),
                        order.shopName(),
                        order.primaryItemName(),
                        order.primaryImageFileId() == null ? null : String.valueOf(order.primaryImageFileId()),
                        defaultInt(order.itemCount()),
                        order.orderStatus(),
                        order.paymentStatus(),
                        defaultAmount(order.totalAmount()),
                        order.currencyCode(),
                        order.cancellable(),
                        order.refundPresent(),
                        order.latestRefundStatus(),
                        order.createdAt(),
                        order.updatedAt()
                ))
                .toList();
    }

    public OrderDetailResponse orderDetail(String authorizationHeader, Long userId, Long orderId) {
        ShopOrdersDtos.ConsumerOrderDetailData data = requireSuccess(call(() -> shopOrdersClient.orderDetail(authorizationHeader, userId, orderId)));
        return new OrderDetailResponse(
                data.orderId(),
                data.orderCode(),
                data.shopId(),
                data.shopName(),
                data.orderStatus(),
                data.paymentStatus(),
                data.paymentCode(),
                data.fulfillmentType(),
                data.addressLabel(),
                data.addressLine(),
                defaultAmount(data.subtotalAmount()),
                defaultAmount(data.deliveryFeeAmount()),
                defaultAmount(data.platformFeeAmount()),
                defaultAmount(data.totalAmount()),
                data.currencyCode(),
                data.cancellable(),
                data.createdAt(),
                data.updatedAt(),
                data.items() == null ? List.of() : data.items().stream()
                        .map(item -> new OrderItemLineResponse(
                                null,
                                item.productId(),
                                item.variantId(),
                                item.productName(),
                                item.variantName(),
                                item.imageFileId() == null ? null : String.valueOf(item.imageFileId()),
                                defaultInt(item.quantity()),
                                defaultAmount(item.unitPrice()),
                                defaultAmount(item.lineTotal())
                        ))
                        .toList(),
                data.timeline() == null ? List.of() : data.timeline().stream()
                        .map(event -> new OrderTimelineEventResponse(
                                event.oldStatus(),
                                event.newStatus(),
                                event.reason(),
                                event.changedAt()
                        ))
                        .toList(),
                data.refund() == null ? null : new RefundSummaryResponse(
                        data.refund().refundCode(),
                        data.refund().refundStatus(),
                        defaultAmount(data.refund().requestedAmount()),
                        defaultAmount(data.refund().approvedAmount()),
                        data.refund().reason(),
                        data.refund().initiatedAt(),
                        data.refund().completedAt()
                )
        );
    }

    public CartResponse addItem(String authorizationHeader, Long userId, CartAddItemRequest request) {
        return mapCart(requireSuccess(call(() -> shopOrdersClient.addCartItem(
                authorizationHeader,
                userId,
                new ShopOrdersDtos.ConsumerCartAddItemRequest(
                        request.productId(),
                        request.variantId(),
                        request.quantity(),
                        request.optionIds(),
                        request.cookingRequest()
                )
        ))));
    }

    public CartResponse updateItem(String authorizationHeader, Long userId, Long itemId, CartUpdateItemRequest request) {
        return mapCart(requireSuccess(call(() -> shopOrdersClient.updateCartItem(
                authorizationHeader,
                userId,
                itemId,
                new ShopOrdersDtos.ConsumerCartUpdateItemRequest(
                        request.quantity(),
                        request.optionIds(),
                        request.cookingRequest()
                )
        ))));
    }

    public CartResponse removeItem(String authorizationHeader, Long userId, Long itemId) {
        return mapCart(requireSuccess(call(() -> shopOrdersClient.removeCartItem(authorizationHeader, userId, itemId))));
    }

    public CheckoutPreviewResponse preview(String authorizationHeader, Long userId, CheckoutPreviewRequest request) {
        ShopOrdersDtos.ConsumerCheckoutPreviewData data = requireSuccess(call(() -> shopOrdersClient.checkoutPreview(
                authorizationHeader,
                userId,
                new ShopOrdersDtos.ConsumerCheckoutPreviewRequest(request.addressId(), request.fulfillmentType())
        )));
        return new CheckoutPreviewResponse(
                null,
                data.shopId(),
                data.shopName(),
                data.addressId(),
                data.addressLabel(),
                data.addressLine(),
                data.fulfillmentType(),
                defaultInt(data.itemCount()),
                defaultAmount(data.subtotal()),
                defaultAmount(data.deliveryFee()),
                defaultAmount(data.platformFee()),
                defaultAmount(data.totalAmount()),
                data.currencyCode(),
                data.shopOpen(),
                data.closingSoon(),
                data.acceptsOrders(),
                data.canPlaceOrder(),
                data.issues() == null ? List.of() : data.issues()
        );
    }

    public PlaceOrderResponse place(String authorizationHeader, Long userId, PlaceOrderRequest request) {
        ShopOrdersDtos.ConsumerPlaceOrderResponse data = requireSuccess(call(() -> shopOrdersClient.placeOrder(
                authorizationHeader,
                userId,
                new ShopOrdersDtos.ConsumerPlaceOrderRequest(request.addressId(), request.fulfillmentType())
        )));
        return new PlaceOrderResponse(
                data.orderId(),
                data.orderCode(),
                data.orderStatus(),
                data.paymentStatus(),
                data.paymentId(),
                data.paymentCode(),
                defaultAmount(data.totalAmount()),
                data.currencyCode(),
                data.nextAction()
        );
    }

    public UserPaymentDtos.PaymentStatusResponse paymentStatus(String authorizationHeader, Long userId, String paymentCode) {
        ShopOrdersDtos.ConsumerPaymentStatusData data = requireSuccess(call(() -> shopOrdersClient.paymentStatus(
                authorizationHeader,
                userId,
                paymentCode
        )));
        return new UserPaymentDtos.PaymentStatusResponse(
                data.paymentId(),
                data.paymentCode(),
                data.payableType(),
                data.payableId(),
                data.paymentStatus(),
                defaultAmount(data.amount()),
                data.currencyCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.latestAttemptStatus(),
                data.latestGatewayTransactionId(),
                data.initiatedAt(),
                data.completedAt()
        );
    }

    public UserPaymentDtos.PaymentInitiateResponse initiatePayment(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentInitiateRequest request
    ) {
        ShopOrdersDtos.ConsumerPaymentInitiateData data = requireSuccess(call(() -> shopOrdersClient.initiatePayment(
                authorizationHeader,
                userId,
                paymentCode,
                new ShopOrdersDtos.ConsumerPaymentInitiateRequest(request == null ? null : request.gatewayName())
        )));
        return new UserPaymentDtos.PaymentInitiateResponse(
                data.paymentId(),
                data.paymentCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.gatewayKeyId(),
                defaultAmount(data.amount()),
                data.currencyCode(),
                data.paymentStatus(),
                data.payableType(),
                data.payableId()
        );
    }

    public UserPaymentDtos.PaymentStatusResponse verifyPayment(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentVerifyRequest request
    ) {
        ShopOrdersDtos.ConsumerPaymentStatusData data = requireSuccess(call(() -> shopOrdersClient.verifyPayment(
                authorizationHeader,
                userId,
                paymentCode,
                new ShopOrdersDtos.ConsumerPaymentVerifyRequest(
                        request.gatewayOrderId(),
                        request.gatewayPaymentId(),
                        request.razorpaySignature()
                )
        )));
        return new UserPaymentDtos.PaymentStatusResponse(
                data.paymentId(),
                data.paymentCode(),
                data.payableType(),
                data.payableId(),
                data.paymentStatus(),
                defaultAmount(data.amount()),
                data.currencyCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.latestAttemptStatus(),
                data.latestGatewayTransactionId(),
                data.initiatedAt(),
                data.completedAt()
        );
    }

    public UserPaymentDtos.PaymentStatusResponse failPayment(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentFailureRequest request
    ) {
        ShopOrdersDtos.ConsumerPaymentStatusData data = requireSuccess(call(() -> shopOrdersClient.failPayment(
                authorizationHeader,
                userId,
                paymentCode,
                new ShopOrdersDtos.ConsumerPaymentFailureRequest(
                        request == null ? null : request.gatewayOrderId(),
                        request == null ? null : request.failureCode(),
                        request == null ? null : request.failureMessage()
                )
        )));
        return new UserPaymentDtos.PaymentStatusResponse(
                data.paymentId(),
                data.paymentCode(),
                data.payableType(),
                data.payableId(),
                data.paymentStatus(),
                defaultAmount(data.amount()),
                data.currencyCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.latestAttemptStatus(),
                data.latestGatewayTransactionId(),
                data.initiatedAt(),
                data.completedAt()
        );
    }

    private CartResponse mapCart(ShopOrdersDtos.ConsumerCartData data) {
        return new CartResponse(
                null,
                data.userId(),
                data.shopId(),
                data.shopName(),
                data.currencyCode(),
                data.cartContext(),
                defaultInt(data.itemCount()),
                defaultAmount(data.subtotal()),
                data.items() == null ? List.of() : data.items().stream()
                        .map(item -> new CartItemResponse(
                                item.itemId(),
                                item.lineKey(),
                                item.productId(),
                                item.variantId(),
                                item.productName(),
                                item.variantName(),
                                defaultInt(item.quantity()),
                                defaultAmount(item.unitPrice()),
                                defaultAmount(item.lineTotal()),
                                item.imageObjectKey(),
                                item.selectedOptions() == null ? List.of() : item.selectedOptions(),
                                item.cookingRequest()
                        ))
                        .toList()
        );
    }

    private <T> ShopOrdersApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.BadRequest exception) {
            log.warn("Shop order service rejected request status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new BadRequestException(extractMessage(exception));
        } catch (FeignException.NotFound exception) {
            log.debug("Shop order service returned not found status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new com.msa.userapp.common.exception.NotFoundException(extractMessage(exception));
        } catch (FeignException exception) {
            log.error("Shop order service call failed status={}", exception.status(), exception);
            throw new BadRequestException("Shop order service is unavailable right now");
        }
    }

    private <T> T requireSuccess(ShopOrdersApiResponse<T> response) {
        if (response == null || !response.success()) {
            log.warn("Shop order service returned unsuccessful response message={}",
                    response == null ? "null response" : response.message());
            throw new BadRequestException(response == null || response.message() == null || response.message().isBlank()
                    ? "Shop order request failed"
                    : response.message());
        }
        return response.data();
    }

    private String extractMessage(FeignException exception) {
        String content = exception.contentUTF8();
        return content == null || content.isBlank() ? "Shop order request failed" : content;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @FunctionalInterface
    private interface FeignCall<T> {
        ShopOrdersApiResponse<T> execute();
    }
}
