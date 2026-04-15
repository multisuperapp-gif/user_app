package com.msa.userapp.modules.service.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceBookingService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ServiceDiscoveryQueryService serviceDiscoveryQueryService;

    public ServiceBookingService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ServiceDiscoveryQueryService serviceDiscoveryQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.serviceDiscoveryQueryService = serviceDiscoveryQueryService;
    }

    @Transactional
    public ServiceApiDtos.DirectServiceBookingResponse createDirectBooking(
            Long userId,
            ServiceApiDtos.DirectServiceBookingRequest request
    ) {
        if (request.providerId() == null) {
            throw new BadRequestException("Service provider id is required");
        }
        Long addressId = serviceDiscoveryQueryService.resolveDefaultAddressId(userId, request.addressId());
        ServiceTargetRow target = requireBookingTarget(
                userId,
                request.providerId(),
                request.categoryId(),
                request.subcategoryId()
        );
        if (target.visitingCharge() == null || target.visitingCharge().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Visiting charge is not available for this service");
        }

        String bookingCode = generateCode("SBK");
        Long bookingId = insertBooking(
                bookingCode,
                userId,
                request.providerId(),
                addressId,
                target.visitingCharge()
        );
        insertBookingLineItem(
                bookingId,
                target.serviceName(),
                target.providerServiceId(),
                target.visitingCharge()
        );
        insertBookingStatusHistory(bookingId, userId, "PAYMENT_PENDING");

        String paymentCode = generateCode("PAY");
        Long paymentId = insertPayment(bookingId, userId, target.visitingCharge(), paymentCode);

        return new ServiceApiDtos.DirectServiceBookingResponse(
                bookingId,
                bookingCode,
                "PAYMENT_PENDING",
                "PENDING",
                paymentId,
                paymentCode,
                target.visitingCharge(),
                "INR",
                target.providerName(),
                target.serviceName()
        );
    }

    private ServiceTargetRow requireBookingTarget(Long userId, Long providerId, Long categoryId, Long subcategoryId) {
        List<ServiceTargetRow> rows = jdbcTemplate.query("""
                SELECT
                    sp.id AS provider_id,
                    COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS provider_name,
                    ps.id AS provider_service_id,
                    ps.service_name,
                    COALESCE(ppr.visiting_charge, 0.00) AS visiting_charge
                FROM service_providers sp
                INNER JOIN users u ON u.id = sp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                INNER JOIN provider_services ps
                    ON ps.provider_id = sp.id
                   AND ps.is_active = 1
                INNER JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
                LEFT JOIN provider_pricing_rules ppr ON ppr.provider_service_id = ps.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS active_booking_count
                    FROM bookings
                    WHERE provider_entity_type = 'SERVICE_PROVIDER'
                      AND booking_status IN ('ACCEPTED', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                    GROUP BY provider_entity_id
                ) active_bookings ON active_bookings.provider_entity_id = sp.id
                WHERE sp.id = :providerId
                  AND sp.approval_status = 'APPROVED'
                  AND sp.online_status = 1
                  AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0
                  AND u.id <> :userId
                  AND (:categoryId IS NULL OR psc.category_id = :categoryId)
                  AND (:subcategoryId IS NULL OR psc.id = :subcategoryId)
                ORDER BY
                    CASE WHEN :subcategoryId IS NOT NULL AND psc.id = :subcategoryId THEN 0 ELSE 1 END,
                    CASE WHEN :categoryId IS NOT NULL AND psc.category_id = :categoryId THEN 0 ELSE 1 END,
                    ps.id ASC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("providerId", providerId)
                .addValue("categoryId", categoryId)
                .addValue("subcategoryId", subcategoryId), (rs, rowNum) -> new ServiceTargetRow(
                rs.getLong("provider_id"),
                rs.getString("provider_name"),
                rs.getLong("provider_service_id"),
                rs.getString("service_name"),
                rs.getBigDecimal("visiting_charge")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Service provider is offline, booked, or not available");
        }
        return rows.getFirst();
    }

    private Long insertBooking(
            String bookingCode,
            Long userId,
            Long providerId,
            Long addressId,
            BigDecimal totalEstimatedAmount
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO bookings (
                    booking_code,
                    booking_type,
                    user_id,
                    provider_entity_type,
                    provider_entity_id,
                    address_id,
                    scheduled_start_at,
                    booking_status,
                    payment_status,
                    subtotal_amount,
                    total_estimated_amount,
                    currency_code
                ) VALUES (
                    :bookingCode,
                    'SERVICE',
                    :userId,
                    'SERVICE_PROVIDER',
                    :providerId,
                    :addressId,
                    :scheduledStartAt,
                    'PAYMENT_PENDING',
                    'PENDING',
                    :totalEstimatedAmount,
                    :totalEstimatedAmount,
                    'INR'
                )
                """, new MapSqlParameterSource()
                .addValue("bookingCode", bookingCode)
                .addValue("userId", userId)
                .addValue("providerId", providerId)
                .addValue("addressId", addressId)
                .addValue("scheduledStartAt", LocalDateTime.now().plusMinutes(30))
                .addValue("totalEstimatedAmount", totalEstimatedAmount), keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create service booking");
        }
        return key.longValue();
    }

    private void insertBookingLineItem(
            Long bookingId,
            String serviceName,
            Long providerServiceId,
            BigDecimal priceSnapshot
    ) {
        jdbcTemplate.update("""
                INSERT INTO booking_line_items (
                    booking_id,
                    service_name,
                    service_ref_type,
                    service_ref_id,
                    quantity,
                    price_snapshot
                ) VALUES (
                    :bookingId,
                    :serviceName,
                    'PROVIDER_SERVICE',
                    :providerServiceId,
                    1,
                    :priceSnapshot
                )
                """, new MapSqlParameterSource()
                .addValue("bookingId", bookingId)
                .addValue("serviceName", serviceName)
                .addValue("providerServiceId", providerServiceId)
                .addValue("priceSnapshot", priceSnapshot));
    }

    private void insertBookingStatusHistory(Long bookingId, Long userId, String newStatus) {
        jdbcTemplate.update("""
                INSERT INTO booking_status_history (
                    booking_id,
                    old_status,
                    new_status,
                    changed_by_user_id,
                    changed_at
                ) VALUES (
                    :bookingId,
                    NULL,
                    :newStatus,
                    :userId,
                    :changedAt
                )
                """, new MapSqlParameterSource()
                .addValue("bookingId", bookingId)
                .addValue("newStatus", newStatus)
                .addValue("userId", userId)
                .addValue("changedAt", LocalDateTime.now()));
    }

    private Long insertPayment(Long bookingId, Long userId, BigDecimal amount, String paymentCode) {
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
                    'BOOKING',
                    :bookingId,
                    :userId,
                    'INITIATED',
                    :amount,
                    'INR',
                    :initiatedAt
                )
                """, new MapSqlParameterSource()
                .addValue("paymentCode", paymentCode)
                .addValue("bookingId", bookingId)
                .addValue("userId", userId)
                .addValue("amount", amount)
                .addValue("initiatedAt", LocalDateTime.now()), keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create service payment");
        }
        return key.longValue();
    }

    private static String generateCode(String prefix) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + raw;
    }

    private record ServiceTargetRow(
            Long providerId,
            String providerName,
            Long providerServiceId,
            String serviceName,
            BigDecimal visitingCharge
    ) {
    }
}
