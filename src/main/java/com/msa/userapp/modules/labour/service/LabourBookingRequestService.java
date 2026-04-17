package com.msa.userapp.modules.labour.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentRequestClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentRequestDtos;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LabourBookingRequestService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LabourQueryService labourQueryService;
    private final BookingPaymentRequestClient bookingPaymentRequestClient;

    public LabourBookingRequestService(
            NamedParameterJdbcTemplate jdbcTemplate,
            LabourQueryService labourQueryService,
            BookingPaymentRequestClient bookingPaymentRequestClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.labourQueryService = labourQueryService;
        this.bookingPaymentRequestClient = bookingPaymentRequestClient;
    }

    @Transactional(readOnly = true)
    public LabourApiDtos.DirectLabourBookingResponse createDirectBookingRequest(
            String authorizationHeader,
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

        BookingPaymentRequestDtos.BookingRequestData data = requireData(call(
                () -> bookingPaymentRequestClient.create(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.CreateBookingRequest(
                                "LABOUR",
                                "DIRECT",
                                userId,
                                addressId,
                                LocalDateTime.now().plusMinutes(30),
                                "LABOUR",
                                request.labourId(),
                                provider.categoryId(),
                                null,
                                bookingPeriod,
                                amount,
                                amount,
                                address.latitude(),
                                address.longitude()
                        )
                )
        ));

        return new LabourApiDtos.DirectLabourBookingResponse(
                data.id(),
                data.requestCode(),
                data.requestStatus(),
                amount,
                "INR",
                provider.fullName()
        );
    }

    @Transactional(readOnly = true)
    public LabourApiDtos.LabourBookingRequestStatusResponse fetchRequestStatus(
            String authorizationHeader,
            Long userId,
            Long requestId
    ) {
        BookingPaymentRequestDtos.UserBookingRequestStatusData data = requireData(call(
                () -> bookingPaymentRequestClient.status(authorizationHeader, userId, requestId)
        ));
        return new LabourApiDtos.LabourBookingRequestStatusResponse(
                data.requestId(),
                data.requestCode(),
                data.requestStatus(),
                data.providerName(),
                data.providerPhone(),
                data.quotedPriceAmount(),
                data.distanceKm(),
                data.bookingId(),
                data.bookingCode(),
                data.bookingStatus(),
                data.paymentStatus(),
                canMakePayment(data)
        );
    }

    @Transactional(readOnly = true)
    public LabourApiDtos.LabourBookingPaymentResponse initiatePayment(
            String authorizationHeader,
            Long userId,
            Long requestId
    ) {
        BookingPaymentRequestDtos.UserBookingRequestStatusData status = requireData(call(
                () -> bookingPaymentRequestClient.status(authorizationHeader, userId, requestId)
        ));
        if (!canMakePayment(status) || status.bookingId() == null) {
            throw new BadRequestException("Payment is allowed only after labour accepts the booking request");
        }
        BookingPaymentRequestDtos.BookingPaymentData payment = requireData(call(
                () -> bookingPaymentRequestClient.initiateBookingPayment(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.InitiateBookingPaymentRequest(status.bookingId())
                )
        ));
        return new LabourApiDtos.LabourBookingPaymentResponse(
                payment.bookingId(),
                payment.bookingCode(),
                payment.paymentCode(),
                payment.amount(),
                payment.currencyCode()
        );
    }

    private boolean canMakePayment(BookingPaymentRequestDtos.UserBookingRequestStatusData data) {
        return data.bookingId() != null
                && "PAYMENT_PENDING".equalsIgnoreCase(data.bookingStatus())
                && (data.paymentStatus() == null || "UNPAID".equalsIgnoreCase(data.paymentStatus()));
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
                LEFT JOIN labour_pricing lpr
                    ON lpr.labour_id = lp.id
                   AND (:categoryId IS NULL OR lpr.category_id = :categoryId)
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

    private static <T> T requireData(BookingPaymentApiResponse<T> response) {
        if (response == null) {
            throw new BadRequestException("Booking service returned an empty response");
        }
        if (!response.success()) {
            throw new BadRequestException(
                    response.message() == null || response.message().isBlank()
                            ? "Booking request failed"
                            : response.message()
            );
        }
        if (response.data() == null) {
            throw new BadRequestException("Booking service returned no data");
        }
        return response.data();
    }

    private <T> BookingPaymentApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.NotFound exception) {
            throw new NotFoundException("Booking request not found");
        } catch (FeignException.BadRequest exception) {
            throw new BadRequestException(extractMessage(exception));
        } catch (FeignException exception) {
            throw new BadRequestException("Booking backend is unavailable right now. Please try again shortly.");
        }
    }

    private static String extractMessage(FeignException exception) {
        String content = exception.contentUTF8();
        if (content != null && !content.isBlank()) {
            return content;
        }
        return "Booking request failed";
    }

    @FunctionalInterface
    private interface FeignCall<T> {
        BookingPaymentApiResponse<T> execute();
    }

    private record AddressRow(
            Long id,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
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
}
