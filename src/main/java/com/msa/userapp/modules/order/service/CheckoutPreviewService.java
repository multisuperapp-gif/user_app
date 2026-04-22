package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.cart.dto.CartResponse;
import com.msa.userapp.modules.cart.service.CartService;
import com.msa.userapp.modules.order.dto.CheckoutPreviewRequest;
import com.msa.userapp.modules.order.dto.CheckoutPreviewResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutPreviewService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CartService cartService;

    public CheckoutPreviewService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CartService cartService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cartService = cartService;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(Long userId, CheckoutPreviewRequest request) {
        CartResponse cart = cartService.getActiveCart(userId);
        if (cart.cartId() == null || cart.items().isEmpty()) {
            throw new BadRequestException("Active cart is empty");
        }

        Long addressId = resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = loadAddress(userId, addressId);
        ShopRuleRow shop = loadShopRule(cart.shopId());

        List<String> issues = new ArrayList<>();
        boolean outOfStockPresent = hasOutOfStockItems(cart.cartId());
        if (outOfStockPresent) {
            issues.add("One or more items are out of stock");
        }
        if (!shop.acceptsOrders()) {
            issues.add("Shop is not accepting orders right now");
        }
        if (cart.subtotal().compareTo(shop.minOrderAmount()) < 0) {
            issues.add("Minimum order amount not reached");
        }

        String fulfillmentType = normalizeFulfillmentType(request.fulfillmentType());
        BigDecimal deliveryFee = "PICKUP".equals(fulfillmentType)
                ? BigDecimal.ZERO
                : calculateDeliveryFee(shop, cart.subtotal());
        BigDecimal platformFee = BigDecimal.ZERO;
        BigDecimal totalAmount = cart.subtotal()
                .add(deliveryFee)
                .add(platformFee)
                .setScale(2, RoundingMode.HALF_UP);

        return new CheckoutPreviewResponse(
                cart.cartId(),
                cart.shopId(),
                coalesce(cart.shopName(), shop.shopName()),
                address.addressId(),
                address.label(),
                address.addressLine(),
                fulfillmentType,
                cart.itemCount(),
                cart.subtotal(),
                deliveryFee,
                platformFee,
                totalAmount,
                cart.currencyCode(),
                shop.openNow(),
                shop.closingSoon(),
                shop.acceptsOrders(),
                issues.isEmpty(),
                List.copyOf(issues)
        );
    }

    public Long resolveDefaultAddressId(Long userId, Long explicitAddressId) {
        if (explicitAddressId != null) {
            List<Long> rows = jdbcTemplate.query("""
                    SELECT id
                    FROM user_addresses
                    WHERE id = :addressId
                      AND user_id = :userId
                      AND address_scope = 'CONSUMER'
                      AND is_hidden = 0
                    LIMIT 1
                    """, Map.of("addressId", explicitAddressId, "userId", userId), (rs, rowNum) -> rs.getLong("id"));
            if (rows.isEmpty()) {
                throw new NotFoundException("Address not found for this user");
            }
            return rows.getFirst();
        }
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM user_addresses
                WHERE user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                ORDER BY is_default DESC, id ASC
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new NotFoundException("Please add an address before checkout");
        }
        return rows.getFirst();
    }

    private AddressRow loadAddress(Long userId, Long addressId) {
        List<AddressRow> rows = jdbcTemplate.query("""
                SELECT
                    id,
                    label,
                    CONCAT(address_line1, COALESCE(CONCAT(', ', address_line2), ''), ', ', city, ', ', postal_code) AS address_line
                FROM user_addresses
                WHERE id = :addressId
                  AND user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                LIMIT 1
                """, Map.of("addressId", addressId, "userId", userId), (rs, rowNum) -> new AddressRow(
                rs.getLong("id"),
                rs.getString("label"),
                rs.getString("address_line")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Address not found");
        }
        return rows.getFirst();
    }

    private ShopRuleRow loadShopRule(Long shopId) {
        List<ShopRuleRow> rows = jdbcTemplate.query("""
                SELECT
                    s.id AS shop_id,
                    s.shop_name,
                    COALESCE(sdr.delivery_fee, 0.00) AS delivery_fee,
                    COALESCE(sdr.free_delivery_above, 999999999.99) AS free_delivery_above,
                    COALESCE(sdr.min_order_amount, 0.00) AS min_order_amount,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time THEN 1
                        ELSE 0
                    END AS open_now,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time
                         AND CURRENT_TIME() >= SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.closing_soon_minutes, 60) * 60))
                        THEN 1
                        ELSE 0
                    END AS closing_soon,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.order_cutoff_minutes_before_close, 30) * 60))
                        THEN 1
                        ELSE 0
                    END AS accepts_orders
                FROM shops s
                INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
                LEFT JOIN shop_delivery_rules sdr ON sdr.shop_location_id = sl.id
                LEFT JOIN shop_operating_hours soh
                  ON soh.shop_location_id = sl.id
                 AND soh.weekday = WEEKDAY(CURRENT_DATE())
                WHERE s.id = :shopId
                LIMIT 1
                """, Map.of("shopId", shopId), (rs, rowNum) -> new ShopRuleRow(
                rs.getLong("shop_id"),
                rs.getString("shop_name"),
                rs.getBigDecimal("delivery_fee"),
                rs.getBigDecimal("free_delivery_above"),
                rs.getBigDecimal("min_order_amount"),
                rs.getBoolean("open_now"),
                rs.getBoolean("closing_soon"),
                rs.getBoolean("accepts_orders")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Shop not found");
        }
        return rows.getFirst();
    }

    private boolean hasOutOfStockItems(Long cartId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM cart_items ci
                LEFT JOIN inventory i ON i.variant_id = ci.variant_id
                WHERE ci.cart_id = :cartId
                  AND (
                        COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                        OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                  )
                """, Map.of("cartId", cartId), Integer.class);
        return count != null && count > 0;
    }

    private static String normalizeFulfillmentType(String value) {
        if (value == null || value.isBlank()) {
            return "DELIVERY";
        }
        String normalized = value.trim().toUpperCase();
        if (!normalized.equals("DELIVERY") && !normalized.equals("PICKUP")) {
            throw new BadRequestException("Unsupported fulfillmentType");
        }
        return normalized;
    }

    private static BigDecimal calculateDeliveryFee(ShopRuleRow shop, BigDecimal subtotal) {
        if (subtotal.compareTo(shop.freeDeliveryAbove()) >= 0) {
            return BigDecimal.ZERO;
        }
        return shop.deliveryFee().setScale(2, RoundingMode.HALF_UP);
    }

    private static String coalesce(String first, String fallback) {
        return first != null ? first : fallback;
    }

    private record AddressRow(
            Long addressId,
            String label,
            String addressLine
    ) {
    }

    private record ShopRuleRow(
            Long shopId,
            String shopName,
            BigDecimal deliveryFee,
            BigDecimal freeDeliveryAbove,
            BigDecimal minOrderAmount,
            boolean openNow,
            boolean closingSoon,
            boolean acceptsOrders
    ) {
    }
}
