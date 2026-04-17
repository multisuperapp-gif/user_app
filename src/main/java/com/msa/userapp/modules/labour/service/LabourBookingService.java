package com.msa.userapp.modules.labour.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.util.StringUtils;

@Service
public class LabourBookingService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LabourQueryService labourQueryService;

    public LabourBookingService(
            NamedParameterJdbcTemplate jdbcTemplate,
            LabourQueryService labourQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.labourQueryService = labourQueryService;
    }

    @Transactional
    public LabourApiDtos.DirectLabourBookingResponse createDirectBooking(
            Long userId,
            LabourApiDtos.DirectLabourBookingRequest request
    ) {
        if (request.labourId() == null) {
            throw new BadRequestException("Labour id is required");
        }
        String bookingPeriod = normalizeBookingPeriod(request.bookingPeriod());
        Long addressId = labourQueryService.resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = requireAddress(addressId, userId);

        LabourProviderRow provider = requireLabourProvider(userId, request.labourId(), request.categoryId(), address);
        BigDecimal amount = switch (bookingPeriod) {
            case "HALF_DAY" -> provider.halfDayRate();
            case "FULL_DAY" -> provider.fullDayRate();
            default -> provider.hourlyRate();
        };
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Selected labour pricing is not available");
        }

        String bookingCode = generateCode("LBK");
        Long bookingId = insertBooking(
                null,
                bookingCode,
                "LABOUR",
                userId,
                "LABOUR",
                request.labourId(),
                addressId,
                LocalDateTime.now().plusMinutes(30),
                "PAYMENT_PENDING",
                "PENDING",
                amount
        );
        insertBookingLineItem(
                bookingId,
                provider.categoryName(),
                "LABOUR_SKILL",
                provider.categoryId(),
                amount
        );
        insertBookingStatusHistory(bookingId, userId, "PAYMENT_PENDING");

        String paymentCode = generateCode("PAY");
        Long paymentId = insertPayment(bookingId, userId, amount, paymentCode);

        return new LabourApiDtos.DirectLabourBookingResponse(
                bookingId,
                bookingCode,
                "PAYMENT_PENDING",
                amount,
                "INR",
                provider.fullName()
        );
    }

    @Transactional
    public LabourApiDtos.GroupLabourBookingResponse createGroupRequest(
            Long userId,
            LabourApiDtos.GroupLabourBookingRequest request
    ) {
        if (request.categoryId() == null) {
            throw new BadRequestException("Labour category is required");
        }
        if (request.labourCount() == null || request.labourCount() <= 0) {
            throw new BadRequestException("Please enter number of labour required");
        }
        if (request.maxPrice() == null || request.maxPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Please enter a valid max price");
        }

        String bookingPeriod = normalizeBookingPeriod(request.bookingPeriod());
        Long addressId = labourQueryService.resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = requireAddress(addressId, userId);
        List<CandidateRow> candidates = findMatchingLabourCandidates(
                request.categoryId(),
                bookingPeriod,
                request.maxPrice(),
                address
        );

        String requestCode = generateCode("LREQ");
        LocalDateTime scheduledStart = LocalDateTime.now().plusMinutes(30);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        Long requestId = insertBookingRequest(
                requestCode,
                userId,
                addressId,
                request.categoryId(),
                null,
                scheduledStart,
                expiresAt,
                request.maxPrice(),
                address.latitude(),
                address.longitude()
        );

        for (CandidateRow candidate : candidates) {
            jdbcTemplate.update("""
                    INSERT INTO booking_request_candidates (
                        request_id,
                        provider_entity_type,
                        provider_entity_id,
                        candidate_status,
                        quoted_price_amount,
                        distance_km,
                        notified_at,
                        expires_at
                    ) VALUES (
                        :requestId,
                        'LABOUR',
                        :providerEntityId,
                        'PENDING',
                        :quotedPriceAmount,
                        :distanceKm,
                        :notifiedAt,
                        :expiresAt
                    )
                    """, new MapSqlParameterSource()
                    .addValue("requestId", requestId)
                    .addValue("providerEntityId", candidate.labourId())
                    .addValue("quotedPriceAmount", candidate.price())
                    .addValue("distanceKm", candidate.distanceKm())
                    .addValue("notifiedAt", LocalDateTime.now())
                    .addValue("expiresAt", expiresAt));
        }

        BigDecimal platformAmount = BigDecimal.valueOf(request.labourCount())
                .multiply(BigDecimal.valueOf(25))
                .setScale(2, RoundingMode.HALF_UP);
        return new LabourApiDtos.GroupLabourBookingResponse(
                requestId,
                requestCode,
                candidates.size(),
                request.labourCount(),
                platformAmount,
                "INR",
                "OPEN"
        );
    }

    private LabourProviderRow requireLabourProvider(Long userId, Long labourId, Long categoryId, AddressRow address) {
        List<LabourProviderRow> rows = jdbcTemplate.query("""
                SELECT
                    lp.id AS labour_id,
                    COALESCE(up.full_name, CONCAT('Labour ', lp.id)) AS full_name,
                    COALESCE(MAX(CASE WHEN lc.id = :categoryId THEN lc.id END), MIN(lc.id)) AS category_id,
                    COALESCE(MAX(CASE WHEN lc.id = :categoryId THEN lc.name END), MIN(lc.name), 'General labour') AS category_name,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HOURLY' AND lpr.is_enabled = 1 THEN lpr.hourly_price END), 0.00) AS hourly_rate,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' AND lpr.is_enabled = 1 THEN lpr.half_day_price END), 0.00) AS half_day_rate,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' AND lpr.is_enabled = 1 THEN lpr.full_day_price END), 0.00) AS full_day_rate,
                    MAX(COALESCE(lsa.radius_km, 0)) AS radius_km,
                    MIN(
                        6371 * ACOS(
                            LEAST(
                                1,
                                COS(RADIANS(:latitude)) * COS(RADIANS(lsa.center_latitude))
                                * COS(RADIANS(lsa.center_longitude) - RADIANS(:longitude))
                                + SIN(RADIANS(:latitude)) * SIN(RADIANS(lsa.center_latitude))
                            )
                        )
                    ) AS distance_km
                FROM labour_profiles lp
                INNER JOIN users u ON u.id = lp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                LEFT JOIN labour_skills ls ON ls.labour_id = lp.id
                LEFT JOIN labour_categories lc ON lc.id = ls.category_id
                LEFT JOIN labour_pricing lpr ON lpr.labour_id = lp.id
                LEFT JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS active_booking_count
                    FROM bookings
                    WHERE provider_entity_type = 'LABOUR'
                      AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                    GROUP BY provider_entity_id
                ) active_bookings ON active_bookings.provider_entity_id = lp.id
                WHERE lp.id = :labourId
                  AND lp.approval_status = 'APPROVED'
                  AND lp.online_status = 1
                  AND COALESCE(active_bookings.active_booking_count, 0) = 0
                  AND u.id <> :userId
                  AND (:categoryId IS NULL OR EXISTS (
                        SELECT 1
                        FROM labour_skills ls_filter
                        WHERE ls_filter.labour_id = lp.id
                          AND ls_filter.category_id = :categoryId
                  ))
                GROUP BY lp.id, up.full_name
                HAVING distance_km IS NULL
                    OR radius_km <= 0
                    OR distance_km <= radius_km
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("labourId", labourId)
                .addValue("categoryId", categoryId)
                .addValue("latitude", address.latitude())
                .addValue("longitude", address.longitude()), (rs, rowNum) -> new LabourProviderRow(
                rs.getLong("labour_id"),
                rs.getString("full_name"),
                rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getBigDecimal("hourly_rate"),
                rs.getBigDecimal("half_day_rate"),
                rs.getBigDecimal("full_day_rate")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Labour is offline, booked, or not available");
        }
        return rows.getFirst();
    }

    private List<CandidateRow> findMatchingLabourCandidates(
            Long categoryId,
            String bookingPeriod,
            BigDecimal maxPrice,
            AddressRow address
    ) {
        return jdbcTemplate.query("""
                SELECT
                    lp.id AS labour_id,
                    CASE
                        WHEN :bookingPeriod = 'HALF_DAY' THEN COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' THEN lpr.half_day_price END), 0.00)
                        WHEN :bookingPeriod = 'FULL_DAY' THEN COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' THEN lpr.full_day_price END), 0.00)
                        ELSE COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HOURLY' THEN lpr.hourly_price END), 0.00)
                    END AS price_amount,
                    MAX(COALESCE(lsa.radius_km, 0)) AS radius_km,
                    MIN(
                        6371 * ACOS(
                            LEAST(
                                1,
                                COS(RADIANS(:latitude)) * COS(RADIANS(lsa.center_latitude))
                                * COS(RADIANS(lsa.center_longitude) - RADIANS(:longitude))
                                + SIN(RADIANS(:latitude)) * SIN(RADIANS(lsa.center_latitude))
                            )
                        )
                    ) AS distance_km
                FROM labour_profiles lp
                INNER JOIN labour_skills ls ON ls.labour_id = lp.id
                INNER JOIN labour_pricing lpr
                    ON lpr.labour_id = lp.id
                   AND lpr.category_id = ls.category_id
                   AND lpr.is_enabled = 1
                INNER JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS active_booking_count
                    FROM bookings
                    WHERE provider_entity_type = 'LABOUR'
                      AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                    GROUP BY provider_entity_id
                ) active_bookings ON active_bookings.provider_entity_id = lp.id
                WHERE lp.approval_status = 'APPROVED'
                  AND lp.online_status = 1
                  AND COALESCE(active_bookings.active_booking_count, 0) = 0
                  AND ls.category_id = :categoryId
                  AND lsa.city = :city
                GROUP BY lp.id
                HAVING price_amount > 0
                   AND price_amount <= :maxPrice
                   AND (
                        distance_km IS NULL
                        OR radius_km <= 0
                        OR distance_km <= radius_km
                   )
                ORDER BY distance_km ASC, price_amount ASC, lp.id ASC
                LIMIT 50
                """, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("bookingPeriod", bookingPeriod)
                .addValue("maxPrice", maxPrice)
                .addValue("city", address.city())
                .addValue("latitude", address.latitude())
                .addValue("longitude", address.longitude()), (rs, rowNum) -> new CandidateRow(
                rs.getLong("labour_id"),
                rs.getBigDecimal("price_amount"),
                rs.getBigDecimal("distance_km")
        ));
    }

    private AddressRow requireAddress(Long addressId, Long userId) {
        List<AddressRow> rows = jdbcTemplate.query("""
                SELECT id, city, latitude, longitude
                FROM user_addresses
                WHERE id = :addressId
                  AND user_id = :userId
                LIMIT 1
                """, Map.of("addressId", addressId, "userId", userId), (rs, rowNum) -> new AddressRow(
                rs.getLong("id"),
                rs.getString("city"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Address not found");
        }
        return rows.getFirst();
    }

    private Long insertBookingRequest(
            String requestCode,
            Long userId,
            Long addressId,
            Long categoryId,
            Long subcategoryId,
            LocalDateTime scheduledStartAt,
            LocalDateTime expiresAt,
            BigDecimal maxPrice,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO booking_requests (
                    request_code,
                    booking_type,
                    request_mode,
                    request_status,
                    user_id,
                    address_id,
                    target_provider_entity_type,
                    target_provider_entity_id,
                    category_id,
                    subcategory_id,
                    scheduled_start_at,
                    expires_at,
                    price_max_amount,
                    search_latitude,
                    search_longitude
                ) VALUES (
                    :requestCode,
                    'LABOUR',
                    'BROADCAST',
                    'OPEN',
                    :userId,
                    :addressId,
                    NULL,
                    NULL,
                    :categoryId,
                    :subcategoryId,
                    :scheduledStartAt,
                    :expiresAt,
                    :maxPrice,
                    :latitude,
                    :longitude
                )
                """, new MapSqlParameterSource()
                .addValue("requestCode", requestCode)
                .addValue("userId", userId)
                .addValue("addressId", addressId)
                .addValue("categoryId", categoryId)
                .addValue("subcategoryId", subcategoryId)
                .addValue("scheduledStartAt", scheduledStartAt)
                .addValue("expiresAt", expiresAt)
                .addValue("maxPrice", maxPrice)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude), keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create labour request");
        }
        return key.longValue();
    }

    private Long insertBooking(
            Long bookingRequestId,
            String bookingCode,
            String bookingType,
            Long userId,
            String providerEntityType,
            Long providerEntityId,
            Long addressId,
            LocalDateTime scheduledStartAt,
            String bookingStatus,
            String paymentStatus,
            BigDecimal totalEstimatedAmount
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO bookings (
                    booking_request_id,
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
                    :bookingRequestId,
                    :bookingCode,
                    :bookingType,
                    :userId,
                    :providerEntityType,
                    :providerEntityId,
                    :addressId,
                    :scheduledStartAt,
                    :bookingStatus,
                    :paymentStatus,
                    :totalEstimatedAmount,
                    :totalEstimatedAmount,
                    'INR'
                )
                """, new MapSqlParameterSource()
                .addValue("bookingRequestId", bookingRequestId)
                .addValue("bookingCode", bookingCode)
                .addValue("bookingType", bookingType)
                .addValue("userId", userId)
                .addValue("providerEntityType", providerEntityType)
                .addValue("providerEntityId", providerEntityId)
                .addValue("addressId", addressId)
                .addValue("scheduledStartAt", scheduledStartAt)
                .addValue("bookingStatus", bookingStatus)
                .addValue("paymentStatus", paymentStatus)
                .addValue("totalEstimatedAmount", totalEstimatedAmount), keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create booking");
        }
        return key.longValue();
    }

    private void insertBookingLineItem(
            Long bookingId,
            String serviceName,
            String serviceRefType,
            Long serviceRefId,
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
                    :serviceRefType,
                    :serviceRefId,
                    1,
                    :priceSnapshot
                )
                """, new MapSqlParameterSource()
                .addValue("bookingId", bookingId)
                .addValue("serviceName", serviceName)
                .addValue("serviceRefType", serviceRefType)
                .addValue("serviceRefId", serviceRefId)
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
            throw new IllegalStateException("Could not create labour payment");
        }
        return key.longValue();
    }

    private static String normalizeBookingPeriod(String bookingPeriod) {
        if (!StringUtils.hasText(bookingPeriod)) {
            throw new BadRequestException("Please select Half Day or Full Day");
        }
        return switch (bookingPeriod.trim().toUpperCase().replace(' ', '_')) {
            case "HALF_DAY" -> "HALF_DAY";
            case "FULL_DAY" -> "FULL_DAY";
            case "HOURLY" -> "HOURLY";
            default -> throw new BadRequestException("Unsupported booking period");
        };
    }

    private static String generateCode(String prefix) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + raw;
    }

    private record LabourProviderRow(
            Long labourId,
            String fullName,
            Long categoryId,
            String categoryName,
            BigDecimal hourlyRate,
            BigDecimal halfDayRate,
            BigDecimal fullDayRate
    ) {
    }

    private record CandidateRow(
            Long labourId,
            BigDecimal price,
            BigDecimal distanceKm
    ) {
    }

    private record AddressRow(
            Long addressId,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
