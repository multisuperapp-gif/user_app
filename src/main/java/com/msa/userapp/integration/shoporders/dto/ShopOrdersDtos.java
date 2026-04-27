package com.msa.userapp.integration.shoporders.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ShopOrdersDtos {
    private ShopOrdersDtos() {
    }

    public record ConsumerCartAddItemRequest(
            Long productId,
            Long variantId,
            Integer quantity,
            List<Long> optionIds,
            String cookingRequest
    ) {
    }

    public record ConsumerCartUpdateItemRequest(
            Integer quantity,
            List<Long> optionIds,
            String cookingRequest
    ) {
    }

    public record ConsumerCartItemData(
            Long itemId,
            String lineKey,
            Long productId,
            Long variantId,
            String productName,
            String variantName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String imageObjectKey,
            List<String> selectedOptions,
            String cookingRequest
    ) {
    }

    public record ConsumerCartData(
            Long userId,
            Long shopId,
            String shopName,
            String currencyCode,
            String cartContext,
            Integer itemCount,
            BigDecimal subtotal,
            List<ConsumerCartItemData> items
    ) {
    }

    public record ConsumerCheckoutPreviewRequest(
            Long addressId,
            String fulfillmentType
    ) {
    }

    public record ConsumerCheckoutPreviewData(
            Long userId,
            Long shopId,
            String shopName,
            Long addressId,
            String addressLabel,
            String addressLine,
            String fulfillmentType,
            Integer itemCount,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal platformFee,
            BigDecimal totalAmount,
            String currencyCode,
            boolean shopOpen,
            boolean closingSoon,
            boolean acceptsOrders,
            boolean canPlaceOrder,
            List<String> issues
    ) {
    }

    public record ConsumerPlaceOrderRequest(
            Long addressId,
            String fulfillmentType
    ) {
    }

    public record ConsumerPlaceOrderResponse(
            Long orderId,
            String orderCode,
            String orderStatus,
            String paymentStatus,
            Long paymentId,
            String paymentCode,
            BigDecimal totalAmount,
            String currencyCode,
            String nextAction
    ) {
    }

    public record ConsumerCancelOrderRequest(
            String reason
    ) {
    }

    public record ConsumerOrderSummaryData(
            Long orderId,
            String orderCode,
            Long shopId,
            String shopName,
            String primaryItemName,
            Long primaryImageFileId,
            Integer itemCount,
            String orderStatus,
            String paymentStatus,
            BigDecimal totalAmount,
            String currencyCode,
            boolean cancellable,
            boolean refundPresent,
            String latestRefundStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ConsumerOrderTimelineEventData(
            String oldStatus,
            String newStatus,
            String reason,
            LocalDateTime changedAt
    ) {
    }

    public record ConsumerRefundSummaryData(
            String refundCode,
            String refundStatus,
            BigDecimal requestedAmount,
            BigDecimal approvedAmount,
            String reason,
            LocalDateTime initiatedAt,
            LocalDateTime completedAt
    ) {
    }

    public record ConsumerOrderItemData(
            Long productId,
            Long variantId,
            String productName,
            String variantName,
            Long imageFileId,
            Integer quantity,
            String unitLabel,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }

    public record ConsumerOrderDetailData(
            Long orderId,
            String orderCode,
            Long shopId,
            String shopName,
            String orderStatus,
            String paymentStatus,
            String paymentCode,
            String fulfillmentType,
            String addressLabel,
            String addressLine,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal deliveryFeeAmount,
            BigDecimal platformFeeAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            String currencyCode,
            boolean cancellable,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<ConsumerOrderItemData> items,
            List<ConsumerOrderTimelineEventData> timeline,
            ConsumerRefundSummaryData refund
    ) {
    }

    public record ConsumerPaymentInitiateRequest(
            String gatewayName
    ) {
    }

    public record ConsumerPaymentVerifyRequest(
            String gatewayOrderId,
            String gatewayPaymentId,
            String razorpaySignature
    ) {
    }

    public record ConsumerPaymentFailureRequest(
            String gatewayOrderId,
            String failureCode,
            String failureMessage
    ) {
    }

    public record ConsumerPaymentStatusData(
            Long paymentId,
            String paymentCode,
            String payableType,
            Long payableId,
            String paymentStatus,
            BigDecimal amount,
            String currencyCode,
            String gatewayName,
            String gatewayOrderId,
            String latestAttemptStatus,
            String latestGatewayTransactionId,
            LocalDateTime initiatedAt,
            LocalDateTime completedAt
    ) {
    }

    public record ConsumerPaymentInitiateData(
            Long paymentId,
            String paymentCode,
            String gatewayName,
            String gatewayOrderId,
            String gatewayKeyId,
            BigDecimal amount,
            String currencyCode,
            String paymentStatus,
            String payableType,
            Long payableId
    ) {
    }

    public record PublicHomeBootstrapData(
            List<PublicShopTypeData> shopTypes,
            PublicPageResponse<PublicShopProductCardData> featuredProducts
    ) {
    }

    public record PublicPageResponse<T>(
            List<T> items,
            int page,
            int size,
            boolean hasMore
    ) {
    }

    public record PublicProductDetailData(
            Long productId,
            Long selectedVariantId,
            Long shopId,
            Long shopTypeId,
            Long categoryId,
            String productName,
            String shopName,
            String categoryName,
            String brandName,
            String description,
            String shortDescription,
            String productType,
            String attributesJson,
            BigDecimal avgRating,
            long totalReviews,
            long totalOrders,
            boolean outOfStock,
            List<PublicProductImageData> images,
            List<PublicProductVariantData> variants,
            List<PublicProductOptionGroupData> optionGroups
    ) {
    }

    public record PublicProductImageData(
            Long id,
            String objectKey,
            String imageRole,
            int sortOrder,
            boolean primaryImage
    ) {
    }

    public record PublicProductOptionGroupData(
            Long id,
            String groupName,
            String groupType,
            int minSelect,
            int maxSelect,
            boolean required,
            List<PublicProductOptionData> options
    ) {
    }

    public record PublicProductOptionData(
            Long id,
            String optionName,
            BigDecimal priceDelta,
            boolean defaultOption
    ) {
    }

    public record PublicProductVariantData(
            Long id,
            String variantName,
            BigDecimal mrp,
            BigDecimal sellingPrice,
            boolean defaultVariant,
            boolean active,
            String attributesJson,
            String inventoryStatus,
            boolean outOfStock
    ) {
    }

    public record PublicShopCategoryData(
            Long id,
            Long parentCategoryId,
            Long shopTypeId,
            String name,
            String normalizedName,
            String themeColor,
            boolean comingSoon,
            String comingSoonMessage,
            String imageObjectKey,
            int sortOrder
    ) {
    }

    public record PublicShopProductCardData(
            Long productId,
            Long variantId,
            Long shopId,
            Long shopTypeId,
            Long categoryId,
            String productName,
            String shopName,
            String categoryName,
            String brandName,
            String shortDescription,
            String productType,
            BigDecimal mrp,
            BigDecimal sellingPrice,
            BigDecimal avgRating,
            long totalReviews,
            long totalOrders,
            String inventoryStatus,
            boolean outOfStock,
            int promotionScore,
            String imageObjectKey
    ) {
    }

    public record PublicShopProfileData(
            PublicShopSummaryData shop,
            List<PublicShopCategoryData> categories,
            PublicPageResponse<PublicShopProductCardData> products
    ) {
    }

    public record PublicShopSummaryData(
            Long shopId,
            Long shopTypeId,
            String shopName,
            String shopCode,
            String logoObjectKey,
            String coverObjectKey,
            BigDecimal avgRating,
            long totalReviews,
            String city,
            BigDecimal latitude,
            BigDecimal longitude,
            String deliveryType,
            BigDecimal deliveryRadiusKm,
            BigDecimal minOrderAmount,
            BigDecimal deliveryFee,
            boolean openNow,
            boolean closingSoon,
            boolean acceptsOrders,
            String closesAt
    ) {
    }

    public record PublicShopTypeLandingData(
            PublicShopTypeData shopType,
            List<PublicShopCategoryData> categories,
            PublicPageResponse<PublicShopProductCardData> products,
            PublicPageResponse<PublicShopSummaryData> shops
    ) {
    }

    public record PublicShopTypeData(
            Long id,
            String name,
            String normalizedName,
            String themeColor,
            boolean comingSoon,
            String comingSoonMessage,
            String iconObjectKey,
            String bannerObjectKey,
            int sortOrder
    ) {
    }
}
