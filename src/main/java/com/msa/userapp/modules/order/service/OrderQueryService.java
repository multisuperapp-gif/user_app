package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.order.dto.OrderDetailResponse;
import com.msa.userapp.modules.order.dto.OrderItemLineResponse;
import com.msa.userapp.modules.order.dto.OrderSummaryResponse;
import com.msa.userapp.modules.order.dto.OrderTimelineEventResponse;
import com.msa.userapp.modules.order.dto.RefundSummaryResponse;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrderQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> list(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        return jdbcTemplate.query("""
                SELECT
                    o.id AS order_id,
                    o.order_code,
                    o.shop_id,
                    s.shop_name,
                    COALESCE(primary_item.product_name_snapshot, 'Order item') AS primary_item_name,
                    primary_item.image_file_id_snapshot AS primary_image_file_id,
                    COALESCE(item_counts.item_count, 0) AS item_count,
                    o.order_status,
                    o.payment_status,
                    o.total_amount,
                    o.currency_code,
                    CASE
                        WHEN o.order_status IN ('OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED') THEN 0
                        ELSE 1
                    END AS cancellable,
                    CASE WHEN latest_refund.refund_code IS NULL THEN 0 ELSE 1 END AS refund_present,
                    latest_refund.refund_status AS latest_refund_status,
                    o.created_at,
                    o.updated_at
                FROM orders o
                INNER JOIN shops s ON s.id = o.shop_id
                LEFT JOIN (
                    SELECT order_id, COUNT(1) AS item_count
                    FROM order_items
                    GROUP BY order_id
                ) item_counts ON item_counts.order_id = o.id
                LEFT JOIN order_items primary_item
                  ON primary_item.order_id = o.id
                 AND primary_item.id = (
                    SELECT oi2.id
                    FROM order_items oi2
                    WHERE oi2.order_id = o.id
                    ORDER BY oi2.id ASC
                    LIMIT 1
                 )
                LEFT JOIN (
                    SELECT
                        p.payable_id AS order_id,
                        r.refund_code,
                        r.refund_status
                    FROM refunds r
                    INNER JOIN payments p ON p.id = r.payment_id
                    WHERE p.payable_type = 'ORDER'
                      AND r.id = (
                        SELECT r2.id
                        FROM refunds r2
                        INNER JOIN payments p2 ON p2.id = r2.payment_id
                        WHERE p2.payable_type = 'ORDER'
                          AND p2.payable_id = p.payable_id
                        ORDER BY r2.id DESC
                        LIMIT 1
                      )
                ) latest_refund ON latest_refund.order_id = o.id
                WHERE o.user_id = :userId
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize), (rs, rowNum) -> new OrderSummaryResponse(
                rs.getLong("order_id"),
                rs.getString("order_code"),
                rs.getLong("shop_id"),
                rs.getString("shop_name"),
                rs.getString("primary_item_name"),
                rs.getString("primary_image_file_id"),
                rs.getInt("item_count"),
                rs.getString("order_status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("currency_code"),
                rs.getBoolean("cancellable"),
                rs.getBoolean("refund_present"),
                rs.getString("latest_refund_status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
        ));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse detail(Long userId, Long orderId) {
        List<OrderDetailResponse> rows = jdbcTemplate.query("""
                SELECT
                    o.id AS order_id,
                    o.order_code,
                    o.shop_id,
                    s.shop_name,
                    o.order_status,
                    o.payment_status,
                    p.payment_code,
                    o.fulfillment_type,
                    ua.label AS address_label,
                    CONCAT(
                        ua.address_line1,
                        COALESCE(CONCAT(', ', ua.address_line2), ''),
                        ', ',
                        ua.city,
                        ', ',
                        ua.postal_code
                    ) AS address_line,
                    o.subtotal_amount,
                    o.delivery_fee_amount,
                    o.platform_fee_amount,
                    o.total_amount,
                    o.currency_code,
                    CASE
                        WHEN o.order_status IN ('OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED') THEN 0
                        ELSE 1
                    END AS cancellable,
                    o.created_at,
                    o.updated_at
                FROM orders o
                INNER JOIN shops s ON s.id = o.shop_id
                LEFT JOIN payments p ON p.payable_type = 'ORDER' AND p.payable_id = o.id
                LEFT JOIN user_addresses ua ON ua.id = o.address_id
                WHERE o.id = :orderId
                  AND o.user_id = :userId
                LIMIT 1
                """, Map.of("orderId", orderId, "userId", userId), (rs, rowNum) -> new OrderDetailResponse(
                rs.getLong("order_id"),
                rs.getString("order_code"),
                rs.getLong("shop_id"),
                rs.getString("shop_name"),
                rs.getString("order_status"),
                rs.getString("payment_status"),
                rs.getString("payment_code"),
                rs.getString("fulfillment_type"),
                rs.getString("address_label"),
                rs.getString("address_line"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("delivery_fee_amount"),
                rs.getBigDecimal("platform_fee_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("currency_code"),
                rs.getBoolean("cancellable"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime(),
                List.of(),
                List.of(),
                null
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Order not found");
        }
        OrderDetailResponse base = rows.getFirst();
        return new OrderDetailResponse(
                base.orderId(),
                base.orderCode(),
                base.shopId(),
                base.shopName(),
                base.orderStatus(),
                base.paymentStatus(),
                base.paymentCode(),
                base.fulfillmentType(),
                base.addressLabel(),
                base.addressLine(),
                base.subtotalAmount(),
                base.deliveryFeeAmount(),
                base.platformFeeAmount(),
                base.totalAmount(),
                base.currencyCode(),
                base.cancellable(),
                base.createdAt(),
                base.updatedAt(),
                loadItems(orderId),
                loadTimeline(orderId),
                loadRefund(orderId)
        );
    }

    private List<OrderItemLineResponse> loadItems(Long orderId) {
        return jdbcTemplate.query("""
                SELECT
                    id,
                    product_id,
                    variant_id,
                    product_name_snapshot,
                    variant_name_snapshot,
                    image_file_id_snapshot,
                    quantity,
                    unit_price_snapshot,
                    line_total
                FROM order_items
                WHERE order_id = :orderId
                ORDER BY id ASC
                """, Map.of("orderId", orderId), (rs, rowNum) -> new OrderItemLineResponse(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getObject("variant_id") == null ? null : rs.getLong("variant_id"),
                rs.getString("product_name_snapshot"),
                rs.getString("variant_name_snapshot"),
                rs.getString("image_file_id_snapshot"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price_snapshot"),
                rs.getBigDecimal("line_total")
        ));
    }

    private List<OrderTimelineEventResponse> loadTimeline(Long orderId) {
        return jdbcTemplate.query("""
                SELECT old_status, new_status, reason, changed_at
                FROM order_status_history
                WHERE order_id = :orderId
                ORDER BY changed_at ASC, id ASC
                """, Map.of("orderId", orderId), (rs, rowNum) -> new OrderTimelineEventResponse(
                rs.getString("old_status"),
                rs.getString("new_status"),
                rs.getString("reason"),
                rs.getTimestamp("changed_at").toLocalDateTime()
        ));
    }

    private RefundSummaryResponse loadRefund(Long orderId) {
        List<RefundSummaryResponse> rows = jdbcTemplate.query("""
                SELECT
                    r.refund_code,
                    r.refund_status,
                    r.requested_amount,
                    r.approved_amount,
                    r.reason,
                    r.initiated_at,
                    r.completed_at
                FROM refunds r
                INNER JOIN payments p ON p.id = r.payment_id
                WHERE p.payable_type = 'ORDER'
                  AND p.payable_id = :orderId
                ORDER BY r.id DESC
                LIMIT 1
                """, Map.of("orderId", orderId), (rs, rowNum) -> new RefundSummaryResponse(
                rs.getString("refund_code"),
                rs.getString("refund_status"),
                rs.getBigDecimal("requested_amount"),
                rs.getBigDecimal("approved_amount"),
                rs.getString("reason"),
                rs.getTimestamp("initiated_at") == null ? null : rs.getTimestamp("initiated_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime()
        ));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
