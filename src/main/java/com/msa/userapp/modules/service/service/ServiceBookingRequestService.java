package com.msa.userapp.modules.service.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentRequestClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentRequestDtos;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceBookingRequestService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ServiceDiscoveryQueryService serviceDiscoveryQueryService;
    private final BookingPaymentRequestClient bookingPaymentRequestClient;

    public ServiceBookingRequestService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ServiceDiscoveryQueryService serviceDiscoveryQueryService,
            BookingPaymentRequestClient bookingPaymentRequestClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.serviceDiscoveryQueryService = serviceDiscoveryQueryService;
        this.bookingPaymentRequestClient = bookingPaymentRequestClient;
    }

    @Transactional(readOnly = true)
    public ServiceApiDtos.DirectServiceBookingResponse createDirectBookingRequest(
            String authorizationHeader,
            Long userId,
            ServiceApiDtos.DirectServiceBookingRequest request
    ) {
        if (request.providerId() == null) {
            throw new BadRequestException("Service provider id is required");
        }
        Long addressId = serviceDiscoveryQueryService.resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = requireAddress(addressId, userId);
        ServiceTargetRow target = requireBookingTarget(
                userId,
                request.providerId(),
                request.categoryId(),
                request.subcategoryId(),
                address
        );
        if (target.visitingCharge() == null || target.visitingCharge().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Visiting charge is not available for this service");
        }

        BookingPaymentRequestDtos.BookingRequestData data = requireData(call(
                () -> bookingPaymentRequestClient.create(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.CreateBookingRequest(
                                "SERVICE",
                                "DIRECT",
                                userId,
                                addressId,
                                LocalDateTime.now().plusMinutes(30),
                                "SERVICE_PROVIDER",
                                request.providerId(),
                                request.categoryId(),
                                request.subcategoryId(),
                                null,
                                target.visitingCharge(),
                                target.visitingCharge(),
                                address.latitude(),
                                address.longitude(),
                                1
                        )
                )
        ));

        return new ServiceApiDtos.DirectServiceBookingResponse(
                data.id(),
                data.requestCode(),
                data.requestStatus(),
                target.visitingCharge(),
                "INR",
                target.providerName(),
                target.serviceName()
        );
    }

    @Transactional(readOnly = true)
    public ServiceApiDtos.ServiceBookingRequestStatusResponse fetchRequestStatus(
            String authorizationHeader,
            Long userId,
            Long requestId
    ) {
        BookingPaymentRequestDtos.UserBookingRequestStatusData data = requireData(call(
                () -> bookingPaymentRequestClient.status(authorizationHeader, userId, requestId)
        ));
        return new ServiceApiDtos.ServiceBookingRequestStatusResponse(
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
    public ServiceApiDtos.ServiceBookingPaymentResponse initiatePayment(
            String authorizationHeader,
            Long userId,
            Long requestId
    ) {
        BookingPaymentRequestDtos.UserBookingRequestStatusData status = requireData(call(
                () -> bookingPaymentRequestClient.status(authorizationHeader, userId, requestId)
        ));
        if (!canMakePayment(status) || status.bookingId() == null) {
            throw new BadRequestException("Payment is allowed only after service provider accepts the booking request");
        }
        BookingPaymentRequestDtos.BookingPaymentData payment = requireData(call(
                () -> bookingPaymentRequestClient.initiateBookingPayment(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.InitiateBookingPaymentRequest(status.bookingId(), null)
                )
        ));
        return new ServiceApiDtos.ServiceBookingPaymentResponse(
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

    private ServiceTargetRow requireBookingTarget(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId,
            AddressRow address
    ) {
        List<ServiceTargetRow> rows = jdbcTemplate.query("""
                SELECT
                    sp.id AS provider_id,
                    COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS provider_name,
                    ps.id AS provider_service_id,
                    ps.service_name,
                    COALESCE(ppr.visiting_charge, 0.00) AS visiting_charge,
                    MAX(COALESCE(psa.radius_km, 0)) AS radius_km,
                    MIN(
                        6371 * ACOS(
                            LEAST(
                                1,
                                COS(RADIANS(:latitude)) * COS(RADIANS(psa.center_latitude))
                                * COS(RADIANS(psa.center_longitude) - RADIANS(:longitude))
                                + SIN(RADIANS(:latitude)) * SIN(RADIANS(psa.center_latitude))
                            )
                        )
                    ) AS distance_km
                FROM service_providers sp
                INNER JOIN users u ON u.id = sp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                INNER JOIN provider_services ps
                    ON ps.provider_id = sp.id
                   AND ps.is_active = 1
                INNER JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
                LEFT JOIN provider_pricing_rules ppr ON ppr.provider_service_id = ps.id
                LEFT JOIN provider_service_areas psa ON psa.provider_id = sp.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS active_booking_count
                    FROM bookings
                    WHERE provider_entity_type = 'SERVICE_PROVIDER'
                      AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                    GROUP BY provider_entity_id
                ) active_bookings ON active_bookings.provider_entity_id = sp.id
                WHERE sp.id = :providerId
                  AND sp.approval_status = 'APPROVED'
                  AND sp.online_status = 1
                  AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0
                  AND u.id <> :userId
                  AND (:categoryId IS NULL OR psc.category_id = :categoryId)
                  AND (:subcategoryId IS NULL OR psc.id = :subcategoryId)
                GROUP BY sp.id, up.full_name, ps.id, ps.service_name, ppr.visiting_charge
                HAVING distance_km IS NULL
                    OR radius_km <= 0
                    OR distance_km <= radius_km
                ORDER BY
                    CASE WHEN :subcategoryId IS NOT NULL AND psc.id = :subcategoryId THEN 0 ELSE 1 END,
                    CASE WHEN :categoryId IS NOT NULL AND psc.category_id = :categoryId THEN 0 ELSE 1 END,
                    distance_km ASC,
                    ps.id ASC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("providerId", providerId)
                .addValue("categoryId", categoryId)
                .addValue("subcategoryId", subcategoryId)
                .addValue("latitude", address.latitude())
                .addValue("longitude", address.longitude()), (rs, rowNum) -> new ServiceTargetRow(
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

    private record ServiceTargetRow(
            Long providerId,
            String providerName,
            Long providerServiceId,
            String serviceName,
            BigDecimal visitingCharge
    ) {
    }
}
