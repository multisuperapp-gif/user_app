package com.msa.userapp.modules.service.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import com.msa.userapp.persistence.sql.entity.BookingEntity;
import com.msa.userapp.persistence.sql.entity.BookingLineItemEntity;
import com.msa.userapp.persistence.sql.entity.BookingStatusHistoryEntity;
import com.msa.userapp.persistence.sql.entity.PaymentEntity;
import com.msa.userapp.persistence.sql.repository.BookingLineItemRepository;
import com.msa.userapp.persistence.sql.repository.BookingRepository;
import com.msa.userapp.persistence.sql.repository.BookingStatusHistoryRepository;
import com.msa.userapp.persistence.sql.repository.PaymentRepository;
import com.msa.userapp.persistence.sql.repository.ServiceBookingSupportRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ServiceBookingService {
    private final ServiceBookingSupportRepository serviceBookingSupportRepository;
    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository bookingLineItemRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final ServiceDiscoveryQueryService serviceDiscoveryQueryService;

    public ServiceBookingService(
            ServiceBookingSupportRepository serviceBookingSupportRepository,
            BookingRepository bookingRepository,
            BookingLineItemRepository bookingLineItemRepository,
            BookingStatusHistoryRepository bookingStatusHistoryRepository,
            PaymentRepository paymentRepository,
            ServiceDiscoveryQueryService serviceDiscoveryQueryService
    ) {
        this.serviceBookingSupportRepository = serviceBookingSupportRepository;
        this.bookingRepository = bookingRepository;
        this.bookingLineItemRepository = bookingLineItemRepository;
        this.bookingStatusHistoryRepository = bookingStatusHistoryRepository;
        this.paymentRepository = paymentRepository;
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
        insertPayment(bookingId, userId, target.visitingCharge(), paymentCode);
        log.info("Created direct service booking userId={} bookingId={} providerId={} paymentCode={}",
                userId,
                bookingId,
                request.providerId(),
                paymentCode);

        return new ServiceApiDtos.DirectServiceBookingResponse(
                bookingId,
                bookingCode,
                "PAYMENT_PENDING",
                target.visitingCharge(),
                "INR",
                target.providerName(),
                target.serviceName(),
                false,
                1
        );
    }

    private ServiceTargetRow requireBookingTarget(Long userId, Long providerId, Long categoryId, Long subcategoryId) {
        return serviceBookingSupportRepository
                .findDirectServiceTarget(userId, providerId, categoryId, subcategoryId)
                .map(row -> new ServiceTargetRow(
                        row.getProviderId(),
                        row.getProviderName(),
                        row.getProviderServiceId(),
                        row.getServiceName(),
                        row.getVisitingCharge()
                ))
                .orElseThrow(() -> new NotFoundException("Service provider is offline, booked, or not available"));
    }

    private Long insertBooking(
            String bookingCode,
            Long userId,
            Long providerId,
            Long addressId,
            BigDecimal totalEstimatedAmount
    ) {
        BookingEntity booking = new BookingEntity();
        booking.setBookingCode(bookingCode);
        booking.setBookingType("SERVICE");
        booking.setUserId(userId);
        booking.setProviderEntityType("SERVICE_PROVIDER");
        booking.setProviderEntityId(providerId);
        booking.setAddressId(addressId);
        booking.setScheduledStartAt(LocalDateTime.now().plusMinutes(30));
        booking.setBookingStatus("PAYMENT_PENDING");
        booking.setPaymentStatus("PENDING");
        booking.setSubtotalAmount(totalEstimatedAmount);
        booking.setTotalEstimatedAmount(totalEstimatedAmount);
        booking.setCurrencyCode("INR");
        return bookingRepository.save(booking).getId();
    }

    private void insertBookingLineItem(
            Long bookingId,
            String serviceName,
            Long providerServiceId,
            BigDecimal priceSnapshot
    ) {
        BookingLineItemEntity lineItem = new BookingLineItemEntity();
        lineItem.setBookingId(bookingId);
        lineItem.setServiceName(serviceName);
        lineItem.setServiceRefType("PROVIDER_SERVICE");
        lineItem.setServiceRefId(providerServiceId);
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
