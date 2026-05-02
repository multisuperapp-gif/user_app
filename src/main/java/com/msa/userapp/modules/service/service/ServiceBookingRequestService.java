package com.msa.userapp.modules.service.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentRequestClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentRequestDtos;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import com.msa.userapp.persistence.sql.repository.ServiceBookingSupportRepository;
import com.msa.userapp.persistence.sql.repository.UserAddressRepository;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ServiceBookingRequestService {
    private final ServiceBookingSupportRepository serviceBookingSupportRepository;
    private final UserAddressRepository userAddressRepository;
    private final ServiceDiscoveryQueryService serviceDiscoveryQueryService;
    private final BookingPaymentRequestClient bookingPaymentRequestClient;

    public ServiceBookingRequestService(
            ServiceBookingSupportRepository serviceBookingSupportRepository,
            UserAddressRepository userAddressRepository,
            ServiceDiscoveryQueryService serviceDiscoveryQueryService,
            BookingPaymentRequestClient bookingPaymentRequestClient
    ) {
        this.serviceBookingSupportRepository = serviceBookingSupportRepository;
        this.userAddressRepository = userAddressRepository;
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
        log.info("Created service booking request userId={} requestId={} providerId={} categoryId={}",
                userId,
                data.id(),
                request.providerId(),
                request.categoryId());

        return new ServiceApiDtos.DirectServiceBookingResponse(
                data.id(),
                data.requestCode(),
                data.requestStatus(),
                target.visitingCharge(),
                "INR",
                target.providerName(),
                target.serviceName(),
                false,
                1
        );
    }

    @Transactional(readOnly = true)
    public ServiceApiDtos.DirectServiceBookingResponse createBroadcastBookingRequest(
            String authorizationHeader,
            Long userId,
            ServiceApiDtos.RandomServiceBookingRequest request
    ) {
        if (request.categoryId() == null && request.subcategoryId() == null) {
            throw new BadRequestException("Please choose a service category before random booking");
        }
        Long addressId = serviceDiscoveryQueryService.resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = requireAddress(addressId, userId);

        BookingPaymentRequestDtos.BookingRequestData data = requireData(call(
                () -> bookingPaymentRequestClient.create(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.CreateBookingRequest(
                                "SERVICE",
                                "BROADCAST",
                                userId,
                                addressId,
                                LocalDateTime.now().plusMinutes(30),
                                null,
                                null,
                                request.categoryId(),
                                request.subcategoryId(),
                                null,
                                null,
                                null,
                                address.latitude(),
                                address.longitude(),
                                1
                        )
                )
        ));
        log.info("Created random service booking request userId={} requestId={} categoryId={} subcategoryId={}",
                userId,
                data.id(),
                request.categoryId(),
                request.subcategoryId());

        return new ServiceApiDtos.DirectServiceBookingResponse(
                data.id(),
                data.requestCode(),
                data.requestStatus(),
                null,
                "INR",
                "Matching providers",
                "Selected service",
                true,
                1
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
                canMakePayment(data),
                data.requestedProviderCount(),
                data.acceptedProviderCount(),
                data.pendingProviderCount()
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
        log.info("Initiated service booking payment userId={} requestId={} bookingId={} paymentCode={}",
                userId,
                requestId,
                payment.bookingId(),
                payment.paymentCode());
        return new ServiceApiDtos.ServiceBookingPaymentResponse(
                payment.bookingId(),
                payment.bookingCode(),
                payment.paymentCode(),
                payment.amount(),
                payment.currencyCode()
        );
    }

    public void cancelRequest(
            String authorizationHeader,
            Long userId,
            Long requestId,
            String reason
    ) {
        call(() -> bookingPaymentRequestClient.cancel(
                authorizationHeader,
                userId,
                requestId,
                new BookingPaymentRequestDtos.CancelBookingRequest(reason)
        ));
        log.info("Cancelled service booking request userId={} requestId={}", userId, requestId);
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
        return serviceBookingSupportRepository
                .findRequestServiceTarget(userId, providerId, categoryId, subcategoryId, address.latitude(), address.longitude())
                .map(row -> new ServiceTargetRow(
                        row.getProviderId(),
                        row.getProviderName(),
                        row.getProviderServiceId(),
                        row.getServiceName(),
                        row.getVisitingCharge()
                ))
                .orElseThrow(() -> new NotFoundException("Service provider is offline, booked, or not available"));
    }

    private AddressRow requireAddress(Long addressId, Long userId) {
        return userAddressRepository
                .findByIdAndUserIdAndAddressScopeAndHiddenFalse(addressId, userId, "CONSUMER")
                .map(address -> new AddressRow(
                        address.getId(),
                        address.getCity(),
                        address.getLatitude(),
                        address.getLongitude()
                ))
                .orElseThrow(() -> new NotFoundException("Address not found"));
    }

    private static <T> T requireData(BookingPaymentApiResponse<T> response) {
        if (response == null) {
            log.warn("Booking service returned null response");
            throw new BadRequestException("Booking service returned an empty response");
        }
        if (!response.success()) {
            log.warn("Booking service returned unsuccessful response message={}", response.message());
            throw new BadRequestException(
                    response.message() == null || response.message().isBlank()
                            ? "Booking request failed"
                            : response.message()
            );
        }
        if (response.data() == null) {
            log.warn("Booking service returned success response with no data");
            throw new BadRequestException("Booking service returned no data");
        }
        return response.data();
    }

    private <T> BookingPaymentApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.NotFound exception) {
            log.debug("Booking service returned not found status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new NotFoundException("Booking request not found");
        } catch (FeignException.BadRequest exception) {
            log.warn("Booking service rejected request status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new BadRequestException(extractMessage(exception));
        } catch (FeignException exception) {
            log.error("Booking service call failed status={}", exception.status(), exception);
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
