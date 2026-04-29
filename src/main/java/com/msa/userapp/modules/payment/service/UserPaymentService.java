package com.msa.userapp.modules.payment.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos;
import com.msa.userapp.modules.payment.dto.UserPaymentDtos;
import com.msa.userapp.modules.order.service.ShopOrdersGatewayService;
import com.msa.userapp.persistence.sql.repository.PaymentRepository;
import com.msa.userapp.persistence.sql.entity.PaymentEntity;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserPaymentService {
    private final PaymentRepository paymentRepository;
    private final BookingPaymentClient bookingPaymentClient;
    private final ShopOrdersGatewayService shopOrdersGatewayService;

    public UserPaymentService(
            PaymentRepository paymentRepository,
            BookingPaymentClient bookingPaymentClient,
            ShopOrdersGatewayService shopOrdersGatewayService
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingPaymentClient = bookingPaymentClient;
        this.shopOrdersGatewayService = shopOrdersGatewayService;
    }

    @Transactional(readOnly = true)
    public UserPaymentDtos.PaymentStatusResponse status(String authorizationHeader, Long userId, String paymentCode) {
        PaymentOwnership ownership = resolvePaymentOwnership(userId, paymentCode);
        if (ownership.shopOrder()) {
            log.debug("Routing payment status to shop order service userId={} paymentCode={} payableId={}",
                    userId,
                    paymentCode,
                    ownership.payableId());
            return shopOrdersGatewayService.paymentStatus(authorizationHeader, userId, paymentCode);
        }
        log.debug("Routing payment status to booking payment service userId={} paymentCode={} payableId={}",
                userId,
                paymentCode,
                ownership.payableId());
        return mapStatus(call(() -> bookingPaymentClient.status(authorizationHeader, userId, paymentCode)));
    }

    @Transactional(readOnly = true)
    public UserPaymentDtos.PaymentInitiateResponse initiate(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentInitiateRequest request
    ) {
        PaymentOwnership ownership = resolvePaymentOwnership(userId, paymentCode);
        if (ownership.shopOrder()) {
            log.info("Initiating shop order payment userId={} paymentCode={} payableId={}",
                    userId,
                    paymentCode,
                    ownership.payableId());
            return shopOrdersGatewayService.initiatePayment(authorizationHeader, userId, paymentCode, request);
        }
        log.info("Initiating booking payment userId={} paymentCode={} payableId={}",
                userId,
                paymentCode,
                ownership.payableId());
        BookingPaymentApiResponse<BookingPaymentDtos.PaymentInitiateResponse> response = call(
                () -> bookingPaymentClient.initiate(
                        authorizationHeader,
                        userId,
                        paymentCode,
                        new BookingPaymentDtos.PaymentInitiateRequest(request == null ? null : request.gatewayName())
                )
        );
        BookingPaymentDtos.PaymentInitiateResponse data = requireData(response);
        return new UserPaymentDtos.PaymentInitiateResponse(
                data.paymentId(),
                data.paymentCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.gatewayKeyId(),
                data.amount(),
                data.currencyCode(),
                data.paymentStatus(),
                data.payableType(),
                data.payableId()
        );
    }

    @Transactional(readOnly = true)
    public UserPaymentDtos.PaymentStatusResponse verify(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentVerifyRequest request
    ) {
        PaymentOwnership ownership = resolvePaymentOwnership(userId, paymentCode);
        if (ownership.shopOrder()) {
            log.info("Verifying shop order payment userId={} paymentCode={} payableId={}",
                    userId,
                    paymentCode,
                    ownership.payableId());
            return shopOrdersGatewayService.verifyPayment(authorizationHeader, userId, paymentCode, request);
        }
        log.info("Verifying booking payment userId={} paymentCode={} payableId={}",
                userId,
                paymentCode,
                ownership.payableId());
        return mapStatus(call(
                () -> bookingPaymentClient.verify(
                        authorizationHeader,
                        userId,
                        paymentCode,
                        new BookingPaymentDtos.PaymentVerifyRequest(
                                request.gatewayOrderId(),
                                request.gatewayPaymentId(),
                                request.razorpaySignature()
                        )
                )
        ));
    }

    @Transactional(readOnly = true)
    public UserPaymentDtos.PaymentStatusResponse fail(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentFailureRequest request
    ) {
        PaymentOwnership ownership = resolvePaymentOwnership(userId, paymentCode);
        if (ownership.shopOrder()) {
            log.warn("Recording failed shop order payment userId={} paymentCode={} payableId={}",
                    userId,
                    paymentCode,
                    ownership.payableId());
            return shopOrdersGatewayService.failPayment(authorizationHeader, userId, paymentCode, request);
        }
        log.warn("Recording failed booking payment userId={} paymentCode={} payableId={}",
                userId,
                paymentCode,
                ownership.payableId());
        return mapStatus(call(
                () -> bookingPaymentClient.failure(
                        authorizationHeader,
                        userId,
                        paymentCode,
                        new BookingPaymentDtos.PaymentFailureRequest(
                                request == null ? null : request.gatewayOrderId(),
                                request == null ? null : request.failureCode(),
                                request == null ? null : request.failureMessage()
                        )
                )
        ));
    }

    private PaymentOwnership resolvePaymentOwnership(Long userId, String paymentCode) {
        PaymentEntity payment = paymentRepository.findByPaymentCodeAndPayerUserId(paymentCode, userId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return new PaymentOwnership(
                "ORDER".equalsIgnoreCase(payment.getPayableType()),
                payment.getPayableId()
        );
    }

    private UserPaymentDtos.PaymentStatusResponse mapStatus(
            BookingPaymentApiResponse<BookingPaymentDtos.PaymentStatusResponse> response
    ) {
        BookingPaymentDtos.PaymentStatusResponse data = requireData(response);
        return new UserPaymentDtos.PaymentStatusResponse(
                data.paymentId(),
                data.paymentCode(),
                data.payableType(),
                data.payableId(),
                data.paymentStatus(),
                data.amount(),
                data.currencyCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.latestAttemptStatus(),
                data.latestGatewayTransactionId(),
                data.initiatedAt(),
                data.completedAt()
        );
    }

    private static <T> T requireData(BookingPaymentApiResponse<T> response) {
        if (response == null) {
            log.warn("Payment service returned null response");
            throw new BadRequestException("Payment service returned an empty response");
        }
        if (!response.success()) {
            log.warn("Payment service returned unsuccessful response message={}", response.message());
            throw new BadRequestException(
                    response.message() == null || response.message().isBlank()
                            ? "Payment request failed"
                            : response.message()
            );
        }
        if (response.data() == null) {
            log.warn("Payment service returned success response with no data");
            throw new BadRequestException("Payment service returned no data");
        }
        return response.data();
    }

    private <T> BookingPaymentApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.NotFound exception) {
            log.debug("Payment service returned not found status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new NotFoundException("Payment not found");
        } catch (FeignException.BadRequest exception) {
            log.warn("Payment service rejected request status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new BadRequestException(extractMessage(exception));
        } catch (FeignException exception) {
            log.error("Payment service call failed status={}", exception.status(), exception);
            throw new BadRequestException("Payment service is unavailable right now");
        }
    }

    private static String extractMessage(FeignException exception) {
        String content = exception.contentUTF8();
        if (content != null && !content.isBlank()) {
            return content;
        }
        return "Payment request failed";
    }

    @FunctionalInterface
    private interface FeignCall<T> {
        BookingPaymentApiResponse<T> execute();
    }

    private record PaymentOwnership(
            boolean shopOrder,
            Long payableId
    ) {
    }
}
