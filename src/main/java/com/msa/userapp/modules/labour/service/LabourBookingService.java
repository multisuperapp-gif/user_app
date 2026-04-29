package com.msa.userapp.modules.labour.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import com.msa.userapp.persistence.sql.entity.BookingEntity;
import com.msa.userapp.persistence.sql.entity.BookingLineItemEntity;
import com.msa.userapp.persistence.sql.entity.BookingRequestCandidateEntity;
import com.msa.userapp.persistence.sql.entity.BookingRequestEntity;
import com.msa.userapp.persistence.sql.entity.BookingStatusHistoryEntity;
import com.msa.userapp.persistence.sql.entity.PaymentEntity;
import com.msa.userapp.persistence.sql.repository.AppSettingRepository;
import com.msa.userapp.persistence.sql.repository.BookingLineItemRepository;
import com.msa.userapp.persistence.sql.repository.BookingRepository;
import com.msa.userapp.persistence.sql.repository.BookingRequestCandidateRepository;
import com.msa.userapp.persistence.sql.repository.BookingRequestRepository;
import com.msa.userapp.persistence.sql.repository.BookingStatusHistoryRepository;
import com.msa.userapp.persistence.sql.repository.LabourBookingSupportRepository;
import com.msa.userapp.persistence.sql.repository.PaymentRepository;
import com.msa.userapp.persistence.sql.repository.UserAddressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class LabourBookingService {
    private static final String PLATFORM_FEE_LABOUR_SETTING_KEY = "platform.fee.labour";
    private static final BigDecimal DEFAULT_LABOUR_BOOKING_PERCENT = new BigDecimal("5.00");
    private static final int MAX_GROUP_LABOUR_COUNT = 7;

    private final LabourBookingSupportRepository labourBookingSupportRepository;
    private final UserAddressRepository userAddressRepository;
    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository bookingLineItemRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final BookingRequestCandidateRepository bookingRequestCandidateRepository;
    private final AppSettingRepository appSettingRepository;
    private final LabourQueryService labourQueryService;

    public LabourBookingService(
            LabourBookingSupportRepository labourBookingSupportRepository,
            UserAddressRepository userAddressRepository,
            BookingRepository bookingRepository,
            BookingLineItemRepository bookingLineItemRepository,
            BookingStatusHistoryRepository bookingStatusHistoryRepository,
            PaymentRepository paymentRepository,
            BookingRequestRepository bookingRequestRepository,
            BookingRequestCandidateRepository bookingRequestCandidateRepository,
            AppSettingRepository appSettingRepository,
            LabourQueryService labourQueryService
    ) {
        this.labourBookingSupportRepository = labourBookingSupportRepository;
        this.userAddressRepository = userAddressRepository;
        this.bookingRepository = bookingRepository;
        this.bookingLineItemRepository = bookingLineItemRepository;
        this.bookingStatusHistoryRepository = bookingStatusHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRequestRepository = bookingRequestRepository;
        this.bookingRequestCandidateRepository = bookingRequestCandidateRepository;
        this.appSettingRepository = appSettingRepository;
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
        insertPayment(bookingId, userId, amount, paymentCode);
        log.info("Created direct labour booking userId={} bookingId={} labourId={} paymentCode={}",
                userId,
                bookingId,
                request.labourId(),
                paymentCode);

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
        if (request.labourCount() > MAX_GROUP_LABOUR_COUNT) {
            throw new BadRequestException("Maximum " + MAX_GROUP_LABOUR_COUNT + " labour can be booked at once");
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
        log.debug("Found labour booking candidates categoryId={} bookingPeriod={} maxPrice={} count={}",
                request.categoryId(),
                bookingPeriod,
                request.maxPrice(),
                candidates.size());

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
            BookingRequestCandidateEntity bookingRequestCandidate = new BookingRequestCandidateEntity();
            bookingRequestCandidate.setRequestId(requestId);
            bookingRequestCandidate.setProviderEntityType("LABOUR");
            bookingRequestCandidate.setProviderEntityId(candidate.labourId());
            bookingRequestCandidate.setCandidateStatus("PENDING");
            bookingRequestCandidate.setQuotedPriceAmount(candidate.price());
            bookingRequestCandidate.setDistanceKm(candidate.distanceKm());
            bookingRequestCandidate.setNotifiedAt(LocalDateTime.now());
            bookingRequestCandidate.setExpiresAt(expiresAt);
            bookingRequestCandidateRepository.save(bookingRequestCandidate);
        }
        log.info("Created group labour booking request userId={} requestId={} categoryId={} labourCount={} candidates={}",
                userId,
                requestId,
                request.categoryId(),
                request.labourCount(),
                candidates.size());

        BigDecimal bookingChargePercent = labourBookingChargePercent();
        BigDecimal estimatedLabourAmount = request.maxPrice()
                .multiply(BigDecimal.valueOf(request.labourCount()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformAmount = estimatedLabourAmount
                .multiply(bookingChargePercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new LabourApiDtos.GroupLabourBookingResponse(
                requestId,
                requestCode,
                candidates.size(),
                request.labourCount(),
                bookingChargePercent,
                estimatedLabourAmount,
                platformAmount,
                "INR",
                "OPEN"
        );
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

    private List<CandidateRow> findMatchingLabourCandidates(
            Long categoryId,
            String bookingPeriod,
            BigDecimal maxPrice,
            AddressRow address
    ) {
        return labourBookingSupportRepository.findMatchingCandidates(
                        categoryId,
                        bookingPeriod,
                        maxPrice,
                        address.city(),
                        address.latitude(),
                        address.longitude()
                ).stream()
                .map(row -> new CandidateRow(
                        row.getLabourId(),
                        row.getPriceAmount(),
                        row.getDistanceKm()
                ))
                .toList();
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
        BookingRequestEntity bookingRequest = new BookingRequestEntity();
        bookingRequest.setRequestCode(requestCode);
        bookingRequest.setBookingType("LABOUR");
        bookingRequest.setRequestMode("BROADCAST");
        bookingRequest.setRequestStatus("OPEN");
        bookingRequest.setUserId(userId);
        bookingRequest.setAddressId(addressId);
        bookingRequest.setCategoryId(categoryId);
        bookingRequest.setSubcategoryId(subcategoryId);
        bookingRequest.setScheduledStartAt(scheduledStartAt);
        bookingRequest.setExpiresAt(expiresAt);
        bookingRequest.setPriceMaxAmount(maxPrice);
        bookingRequest.setSearchLatitude(latitude);
        bookingRequest.setSearchLongitude(longitude);
        return bookingRequestRepository.save(bookingRequest).getId();
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
        BookingEntity booking = new BookingEntity();
        booking.setBookingRequestId(bookingRequestId);
        booking.setBookingCode(bookingCode);
        booking.setBookingType(bookingType);
        booking.setUserId(userId);
        booking.setProviderEntityType(providerEntityType);
        booking.setProviderEntityId(providerEntityId);
        booking.setAddressId(addressId);
        booking.setScheduledStartAt(scheduledStartAt);
        booking.setBookingStatus(bookingStatus);
        booking.setPaymentStatus(paymentStatus);
        booking.setSubtotalAmount(totalEstimatedAmount);
        booking.setTotalEstimatedAmount(totalEstimatedAmount);
        booking.setCurrencyCode("INR");
        return bookingRepository.save(booking).getId();
    }

    private void insertBookingLineItem(
            Long bookingId,
            String serviceName,
            String serviceRefType,
            Long serviceRefId,
            BigDecimal priceSnapshot
    ) {
        BookingLineItemEntity lineItem = new BookingLineItemEntity();
        lineItem.setBookingId(bookingId);
        lineItem.setServiceName(serviceName);
        lineItem.setServiceRefType(serviceRefType);
        lineItem.setServiceRefId(serviceRefId);
        lineItem.setQuantity(1);
        lineItem.setPriceSnapshot(priceSnapshot);
        bookingLineItemRepository.save(lineItem);
    }

    private void insertBookingStatusHistory(Long bookingId, Long userId, String newStatus) {
        BookingStatusHistoryEntity history = new BookingStatusHistoryEntity();
        history.setBookingId(bookingId);
        history.setOldStatus(null);
        history.setNewStatus(newStatus);
        history.setChangedByUserId(userId);
        history.setChangedAt(LocalDateTime.now());
        bookingStatusHistoryRepository.save(history);
    }

    private void insertPayment(Long bookingId, Long userId, BigDecimal amount, String paymentCode) {
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentCode(paymentCode);
        payment.setPayableType("BOOKING");
        payment.setPayableId(bookingId);
        payment.setPayerUserId(userId);
        payment.setPaymentStatus("INITIATED");
        payment.setAmount(amount);
        payment.setCurrencyCode("INR");
        payment.setInitiatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
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
