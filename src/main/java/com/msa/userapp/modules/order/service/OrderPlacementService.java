package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.modules.cart.dto.CartItemResponse;
import com.msa.userapp.modules.cart.dto.CartResponse;
import com.msa.userapp.modules.cart.service.CartService;
import com.msa.userapp.modules.order.dto.CheckoutPreviewRequest;
import com.msa.userapp.modules.order.dto.CheckoutPreviewResponse;
import com.msa.userapp.modules.order.dto.PlaceOrderRequest;
import com.msa.userapp.modules.order.dto.PlaceOrderResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPlacementService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CartService cartService;
    private final CheckoutPreviewService checkoutPreviewService;

    public OrderPlacementService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CartService cartService,
            CheckoutPreviewService checkoutPreviewService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cartService = cartService;
        this.checkoutPreviewService = checkoutPreviewService;
    }

    @Transactional
    public PlaceOrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        CheckoutPreviewResponse preview = checkoutPreviewService.preview(
                userId,
                new CheckoutPreviewRequest(request.addressId(), request.fulfillmentType())
        );
        if (!preview.canPlaceOrder()) {
            throw new BadRequestException("Order cannot be placed: " + String.join(", ", preview.issues()));
        }

        CartResponse cart = cartService.getActiveCart(userId);
        if (cart.cartId() == null || cart.items().isEmpty()) {
            throw new BadRequestException("Active cart is empty");
        }

        String orderCode = generateCode("ORD");
        Long orderId = insertOrder(userId, cart, preview, orderCode);
        insertOrderItems(orderId, cart);
        insertOrderStatusHistory(orderId, userId, "PAYMENT_PENDING");

        String paymentCode = generateCode("PAY");
        Long paymentId = insertPayment(orderId, userId, preview.totalAmount(), cart.currencyCode(), paymentCode);

        jdbcTemplate.update("""
                UPDATE carts
                SET cart_status = 'CHECKED_OUT',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :cartId
                """, Map.of("cartId", cart.cartId()));

        return new PlaceOrderResponse(
                orderId,
                orderCode,
                "PAYMENT_PENDING",
                "PENDING",
                paymentId,
                paymentCode,
                preview.totalAmount(),
                cart.currencyCode(),
                "PROCEED_TO_PAYMENT"
        );
    }

    private Long insertOrder(Long userId, CartResponse cart, CheckoutPreviewResponse preview, String orderCode) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderCode", orderCode)
                .addValue("userId", userId)
                .addValue("shopId", cart.shopId())
                .addValue("addressId", preview.addressId())
                .addValue("orderStatus", "PAYMENT_PENDING")
                .addValue("paymentStatus", "PENDING")
                .addValue("fulfillmentType", preview.fulfillmentType())
                .addValue("subtotalAmount", preview.subtotal())
                .addValue("deliveryFeeAmount", preview.deliveryFee())
                .addValue("platformFeeAmount", preview.platformFee())
                .addValue("totalAmount", preview.totalAmount())
                .addValue("currencyCode", preview.currencyCode());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO orders (
                    order_code,
                    user_id,
                    shop_id,
                    address_id,
                    order_status,
                    payment_status,
                    fulfillment_type,
                    subtotal_amount,
                    tax_amount,
                    delivery_fee_amount,
                    platform_fee_amount,
                    packaging_fee_amount,
                    tip_amount,
                    discount_amount,
                    total_amount,
                    currency_code
                ) VALUES (
                    :orderCode,
                    :userId,
                    :shopId,
                    :addressId,
                    :orderStatus,
                    :paymentStatus,
                    :fulfillmentType,
                    :subtotalAmount,
                    0.00,
                    :deliveryFeeAmount,
                    :platformFeeAmount,
                    0.00,
                    0.00,
                    0.00,
                    :totalAmount,
                    :currencyCode
                )
                """, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create order");
        }
        return key.longValue();
    }

    private void insertOrderItems(Long orderId, CartResponse cart) {
        for (CartItemResponse item : cart.items()) {
            if (item.productId() == null) {
                throw new BadRequestException("Cart item is missing product snapshot");
            }
            jdbcTemplate.update("""
                    INSERT INTO order_items (
                        order_id,
                        product_id,
                        variant_id,
                        selected_options_json,
                        product_name_snapshot,
                        variant_name_snapshot,
                        image_file_id_snapshot,
                        shop_name_snapshot,
                        quantity,
                        unit_price_snapshot,
                        tax_snapshot,
                        line_total
                    )
                    SELECT
                        :orderId,
                        :productId,
                        :variantId,
                        ci.selected_options_json,
                        ci.product_name_snapshot,
                        ci.variant_name_snapshot,
                        ci.image_file_id_snapshot,
                        s.shop_name,
                        :quantity,
                        :unitPrice,
                        0.00,
                        :lineTotal
                    FROM cart_items ci
                    INNER JOIN carts c ON c.id = ci.cart_id
                    INNER JOIN shops s ON s.id = c.shop_id
                    WHERE ci.id = :cartItemId
                    """, new MapSqlParameterSource()
                    .addValue("orderId", orderId)
                    .addValue("productId", item.productId())
                    .addValue("variantId", item.variantId())
                    .addValue("quantity", item.quantity())
                    .addValue("unitPrice", item.unitPrice())
                    .addValue("lineTotal", item.lineTotal())
                    .addValue("cartItemId", item.id()));
            jdbcTemplate.update("""
                    UPDATE inventory
                    SET reserved_quantity = reserved_quantity + :quantity,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE variant_id = :variantId
                    """, Map.of("quantity", item.quantity(), "variantId", item.variantId()));
        }
    }

    private void insertOrderStatusHistory(Long orderId, Long userId, String newStatus) {
        jdbcTemplate.update("""
                INSERT INTO order_status_history (
                    order_id,
                    old_status,
                    new_status,
                    changed_by_user_id,
                    changed_at
                ) VALUES (
                    :orderId,
                    NULL,
                    :newStatus,
                    :userId,
                    :changedAt
                )
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("newStatus", newStatus)
                .addValue("userId", userId)
                .addValue("changedAt", LocalDateTime.now()));
    }

    private Long insertPayment(Long orderId, Long userId, BigDecimal amount, String currencyCode, String paymentCode) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO payments (
                    payment_code,
                    payable_type,
                    payable_id,
                    payer_user_id,
                    payment_status,
                    amount,
                    currency_code,
                    initiated_at
                ) VALUES (
                    :paymentCode,
                    'ORDER',
                    :orderId,
                    :userId,
                    'INITIATED',
                    :amount,
                    :currencyCode,
                    :initiatedAt
                )
                """, new MapSqlParameterSource()
                .addValue("paymentCode", paymentCode)
                .addValue("orderId", orderId)
                .addValue("userId", userId)
                .addValue("amount", amount)
                .addValue("currencyCode", currencyCode)
                .addValue("initiatedAt", LocalDateTime.now()), keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create payment");
        }
        return key.longValue();
    }

    private static String generateCode(String prefix) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + raw;
    }
}
