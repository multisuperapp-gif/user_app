package com.msa.userapp.modules.labour.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentRequestClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentRequestDtos;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import com.msa.userapp.persistence.sql.repository.AppSettingRepository;
import com.msa.userapp.persistence.sql.repository.LabourBookingSupportRepository;
import com.msa.userapp.persistence.sql.repository.UserAddressRepository;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class LabourBookingRequestService {
    private static final String PLATFORM_FEE_LABOUR_SETTING_KEY = "platform.fee.labour";
    private static final BigDecimal DEFAULT_LABOUR_BOOKING_PERCENT = new BigDecimal("5.00");
    private static final int MAX_GROUP_LABOUR_COUNT = 7;

    private final LabourBookingSupportRepository labourBookingSupportRepository;
    private final UserAddressRepository userAddressRepository;
    private final AppSettingRepository appSettingRepository;
    private final LabourQueryService labourQueryService;
    private final BookingPaymentRequestClient bookingPaymentRequestClient;

    public LabourBookingRequestService(
            LabourBookingSupportRepository labourBookingSupportRepository,
            UserAddressRepository userAddressRepository,
            AppSettingRepository appSettingRepository,
            LabourQueryService labourQueryService,
            BookingPaymentRequestClient bookingPaymentRequestClient
    ) {
        this.labourBookingSupportRepository = labourBookingSupportRepository;
        this.userAddressRepository = userAddressRepository;
        this.appSettingRepository = appSettingRepository;
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
                                address.longitude(),
                                1
                        )
                )
        ));
        log.info("Created direct labour booking request userId={} requestId={} labourId={} categoryId={}",
                userId,
                data.id(),
                request.labourId(),
                provider.categoryId());

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
    public LabourApiDtos.GroupLabourBookingResponse createGroupBookingRequest(
            String authorizationHeader,
            Long userId,
            LabourApiDtos.GroupLabourBookingRequest request
    ) {
        if (request.categoryId() == null) {
            throw new BadRequestException("Please select a labour type first");
        }
        if (request.labourCount() == null || request.labourCount() <= 0) {
            throw new BadRequestException("Please enter number of labour required");
        }
        if (request.labourCount() > MAX_GROUP_LABOUR_COUNT) {
            throw new BadRequestException("Maximum " + MAX_GROUP_LABOUR_COUNT + " labour can be booked at once");
        }
        if (request.maxPrice() == null || request.maxPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Please enter a valid max price");
        }
        String bookingPeriod = normalizeBookingPeriod(request.bookingPeriod());
        Long addressId = labourQueryService.resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = requireAddress(addressId, userId);

        BookingPaymentRequestDtos.BookingRequestData data = requireData(call(
                () -> bookingPaymentRequestClient.create(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.CreateBookingRequest(
                                "LABOUR",
                                "BROADCAST",
                                userId,
                                addressId,
                                LocalDateTime.now().plusMinutes(30),
                                null,
                                null,
                                request.categoryId(),
                                null,
                                bookingPeriod,
                                BigDecimal.ZERO,
                                request.maxPrice(),
                                address.latitude(),
                                address.longitude(),
                                request.labourCount()
                        )
                )
        ));
        int availableCandidates = data.candidates() == null ? 0 : data.candidates().size();
        log.info("Created group labour booking request userId={} requestId={} categoryId={} labourCount={} candidates={}",
                userId,
                data.id(),
                request.categoryId(),
                request.labourCount(),
                availableCandidates);
        BigDecimal estimatedLabourAmount = request.maxPrice()
                .multiply(BigDecimal.valueOf(request.labourCount()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal bookingChargePercent = labourBookingChargePercent();
        BigDecimal platformAmount = estimatedLabourAmount
                .multiply(bookingChargePercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new LabourApiDtos.GroupLabourBookingResponse(
                data.id(),
                data.requestCode(),
                availableCandidates,
                request.labourCount(),
                bookingChargePercent,
                estimatedLabourAmount,
                platformAmount,
                "INR",
                data.requestStatus()
        );
    }

    @Transactional(readOnly = true)
    public LabourApiDtos.LabourBookingPolicyResponse bookingPolicy() {
        return new LabourApiDtos.LabourBookingPolicyResponse(
                labourBookingChargePercent(),
                "INR",
                MAX_GROUP_LABOUR_COUNT
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
                data.totalAcceptedQuotedPriceAmount(),
                data.totalAcceptedBookingChargeAmount(),
                data.distanceKm(),
                data.requestedProviderCount() == null ? 1 : data.requestedProviderCount(),
                data.acceptedProviderCount() == null ? 0 : data.acceptedProviderCount(),
                data.acceptedProviders() == null
                        ? List.of()
                        : data.acceptedProviders().stream()
                                .map(provider -> new LabourApiDtos.AcceptedProviderResponse(
                                        provider.candidateId(),
                                        provider.providerEntityId(),
                                        provider.providerName(),
                                        provider.quotedPriceAmount()
                                ))
                                .toList(),
                data.pendingProviderCount() == null ? 0 : data.pendingProviderCount(),
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
        if (!canMakePayment(status)) {
            throw new BadRequestException("Payment is allowed only after labour accepts the booking request");
        }
        BookingPaymentRequestDtos.BookingPaymentData payment = requireData(call(
                () -> bookingPaymentRequestClient.initiateBookingPayment(
                        authorizationHeader,
                        userId,
                        new BookingPaymentRequestDtos.InitiateBookingPaymentRequest(
                                status.requestedProviderCount() != null && status.requestedProviderCount() > 1
                                        ? null
                                        : status.bookingId(),
                                status.requestedProviderCount() != null && status.requestedProviderCount() > 1
                                        ? status.requestId()
                                        : null
                        )
                )
        ));
        log.info("Initiated labour booking payment userId={} requestId={} bookingId={} paymentCode={}",
                userId,
                requestId,
                payment.bookingId(),
                payment.paymentCode());
        return new LabourApiDtos.LabourBookingPaymentResponse(
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
        log.info("Cancelled labour booking request userId={} requestId={}", userId, requestId);
    }

    private boolean canMakePayment(BookingPaymentRequestDtos.UserBookingRequestStatusData data) {
        if (data.acceptedProviderCount() != null && data.acceptedProviderCount() > 1) {
            return data.requestId() != null
                    && isRetryablePaymentStatus(data.paymentStatus());
        }
        return data.bookingId() != null
                && "PAYMENT_PENDING".equalsIgnoreCase(data.bookingStatus())
                && isRetryablePaymentStatus(data.paymentStatus());
    }

    private boolean isRetryablePaymentStatus(String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            return true;
        }
        return "UNPAID".equalsIgnoreCase(paymentStatus)
                || "INITIATED".equalsIgnoreCase(paymentStatus)
                || "PENDING".equalsIgnoreCase(paymentStatus)
                || "FAILED".equalsIgnoreCase(paymentStatus);
    }

    private LabourProviderRow requireLabourProvider(Long userId, Long labourId, Long categoryId, AddressRow address) {
        return labourBookingSupportRepository
                .findDirectLabourTarget(userId, labourId, categoryId, address.latitude(), address.longitude())
                .map(row -> new LabourProviderRow(
                        row.getLabourId(),
                        row.getFullName(),
                        row.getCategoryId(),
                        row.getCategoryName(),
                        row.getHourlyRate(),
                        row.getHalfDayRate(),
                        row.getFullDayRate()
                ))
                .orElseThrow(() -> new NotFoundException("Labour is offline, booked, or not available"));
    }

    private BigDecimal labourBookingChargePercent() {
        return appSettingRepository.findBySettingKey(PLATFORM_FEE_LABOUR_SETTING_KEY)
                .map(setting -> {
                    try {
                        BigDecimal value = new BigDecimal(setting.getSettingValue());
                        if (value.compareTo(BigDecimal.ZERO) < 0) {
                            log.warn("Invalid negative labour platform fee setting key={} value={}",
                                    PLATFORM_FEE_LABOUR_SETTING_KEY,
                                    setting.getSettingValue());
                            return DEFAULT_LABOUR_BOOKING_PERCENT;
                        }
                        return value.setScale(2, RoundingMode.HALF_UP);
                    } catch (NumberFormatException exception) {
                        log.warn("Invalid labour platform fee setting key={} value={}",
                                PLATFORM_FEE_LABOUR_SETTING_KEY,
                                setting.getSettingValue());
                        return DEFAULT_LABOUR_BOOKING_PERCENT;
                    }
                })
                .orElse(DEFAULT_LABOUR_BOOKING_PERCENT);
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

    @FunctionalInterface
    private interface FeignCall<T> {
        BookingPaymentApiResponse<T> execute();
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

    private record AddressRow(
            Long id,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
