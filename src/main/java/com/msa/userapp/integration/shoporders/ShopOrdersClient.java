package com.msa.userapp.integration.shoporders;

import com.msa.userapp.integration.shoporders.dto.ShopOrdersApiResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerCartAddItemRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerCartData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerCartUpdateItemRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerCancelOrderRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerCheckoutPreviewData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerCheckoutPreviewRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerOrderDetailData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerOrderSummaryData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPaymentFailureRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPaymentInitiateData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPaymentInitiateRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPaymentStatusData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPaymentVerifyRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPlaceOrderRequest;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.ConsumerPlaceOrderResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicHomeBootstrapData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicPageResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicProductDetailData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicShopCategoryData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicShopProductCardData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicShopProfileData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicShopSummaryData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicShopTypeData;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos.PublicShopTypeLandingData;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "shopOrdersClient", url = "${app.integrations.shop-orders-url}")
public interface ShopOrdersClient {
    @GetMapping("/shop-orders/public/home/bootstrap")
    ShopOrdersApiResponse<PublicHomeBootstrapData> publicHomeBootstrap(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double latitude,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double longitude,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/shop-orders/public/shop/types")
    ShopOrdersApiResponse<List<PublicShopTypeData>> publicShopTypes();

    @GetMapping("/shop-orders/public/shop/categories")
    ShopOrdersApiResponse<List<PublicShopCategoryData>> publicShopCategories(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long shopTypeId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long parentCategoryId
    );

    @GetMapping("/shop-orders/public/shop/products")
    ShopOrdersApiResponse<PublicPageResponse<PublicShopProductCardData>> publicShopProducts(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long shopTypeId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long categoryId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double latitude,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double longitude,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/shop-orders/public/shop/products/{productId}")
    ShopOrdersApiResponse<PublicProductDetailData> publicProductDetail(
            @PathVariable Long productId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long variantId
    );

    @GetMapping("/shop-orders/public/shop/types/{normalizedShopType}/landing")
    ShopOrdersApiResponse<PublicShopTypeLandingData> publicTypeLanding(
            @PathVariable String normalizedShopType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double latitude,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double longitude,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/shop-orders/public/shop/types/{normalizedShopType}/categories")
    ShopOrdersApiResponse<List<PublicShopCategoryData>> publicTypeCategories(
            @PathVariable String normalizedShopType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long parentCategoryId
    );

    @GetMapping("/shop-orders/public/shop/types/{normalizedShopType}/products")
    ShopOrdersApiResponse<PublicPageResponse<PublicShopProductCardData>> publicTypeProducts(
            @PathVariable String normalizedShopType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long categoryId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/shop-orders/public/shop/types/{normalizedShopType}/shops")
    ShopOrdersApiResponse<PublicPageResponse<PublicShopSummaryData>> publicTypeShops(
            @PathVariable String normalizedShopType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double latitude,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Double longitude,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/shop-orders/public/shop/types/{normalizedShopType}/shops/{shopId}")
    ShopOrdersApiResponse<PublicShopProfileData> publicShopProfile(
            @PathVariable String normalizedShopType,
            @PathVariable Long shopId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long categoryId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/shop-orders/cart")
    ShopOrdersApiResponse<ConsumerCartData> cart(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId
    );

    @GetMapping("/shop-orders/orders")
    ShopOrdersApiResponse<java.util.List<ConsumerOrderSummaryData>> orders(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId
    );

    @GetMapping("/shop-orders/orders/{orderId}")
    ShopOrdersApiResponse<ConsumerOrderDetailData> orderDetail(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    );

    @PostMapping("/shop-orders/cart/items")
    ShopOrdersApiResponse<ConsumerCartData> addCartItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ConsumerCartAddItemRequest request
    );

    @PutMapping("/shop-orders/cart/items/{itemId}")
    ShopOrdersApiResponse<ConsumerCartData> updateCartItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId,
            @RequestBody ConsumerCartUpdateItemRequest request
    );

    @DeleteMapping("/shop-orders/cart/items/{itemId}")
    ShopOrdersApiResponse<ConsumerCartData> removeCartItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId
    );

    @PostMapping("/shop-orders/checkout-preview")
    ShopOrdersApiResponse<ConsumerCheckoutPreviewData> checkoutPreview(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ConsumerCheckoutPreviewRequest request
    );

    @PostMapping("/shop-orders/orders/place")
    ShopOrdersApiResponse<ConsumerPlaceOrderResponse> placeOrder(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ConsumerPlaceOrderRequest request
    );

    @PostMapping("/shop-orders/orders/{orderId}/cancel")
    ShopOrdersApiResponse<Void> cancelOrder(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId,
            @RequestBody(required = false) ConsumerCancelOrderRequest request
    );

    @GetMapping("/shop-orders/payments/{paymentCode}")
    ShopOrdersApiResponse<ConsumerPaymentStatusData> paymentStatus(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode
    );

    @PostMapping("/shop-orders/payments/{paymentCode}/initiate")
    ShopOrdersApiResponse<ConsumerPaymentInitiateData> initiatePayment(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) ConsumerPaymentInitiateRequest request
    );

    @PostMapping("/shop-orders/payments/{paymentCode}/verify")
    ShopOrdersApiResponse<ConsumerPaymentStatusData> verifyPayment(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody ConsumerPaymentVerifyRequest request
    );

    @PostMapping("/shop-orders/payments/{paymentCode}/failure")
    ShopOrdersApiResponse<ConsumerPaymentStatusData> failPayment(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) ConsumerPaymentFailureRequest request
    );
}
