package com.msa.userapp.modules.payment.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentDtos;
import com.msa.userapp.modules.payment.dto.UserPaymentDtos;
import feign.FeignException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPaymentService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BookingPaymentClient bookingPaymentClient;

    public UserPaymentService(
            NamedParameterJdbcTemplate jdbcTemplate,
            BookingPaymentClient bookingPaymentClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.bookingPaymentClient = bookingPaymentClient;
    }

    @Transactional(readOnly = true)
    public UserPaymentDtos.PaymentStatusResponse status(String authorizationHeader, Long userId, String paymentCode) {
        validatePaymentOwnership(userId, paymentCode);
        return mapStatus(call(() -> bookingPaymentClient.status(authorizationHeader, userId, paymentCode)));
    }

    @Transactional(readOnly = true)
    public UserPaymentDtos.PaymentInitiateResponse initiate(
            String authorizationHeader,
            Long userId,
            String paymentCode,
            UserPaymentDtos.PaymentInitiateRequest request
    ) {
        validatePaymentOwnership(userId, paymentCode);
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
        validatePaymentOwnership(userId, paymentCode);
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
        validatePaymentOwnership(userId, paymentCode);
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

    private void validatePaymentOwnership(Long userId, String paymentCode) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM payments
                WHERE payment_code = :paymentCode
                  AND payer_user_id = :userId
                LIMIT 1
                """, Map.of("paymentCode", paymentCode, "userId", userId), (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new NotFoundException("Payment not found");
        }
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
            throw new BadRequestException("Payment service returned an empty response");
        }
        if (!response.success()) {
            throw new BadRequestException(
                    response.message() == null || response.message().isBlank()
                            ? "Payment request failed"
                            : response.message()
            );
        }
        if (response.data() == null) {
            throw new BadRequestException("Payment service returned no data");
        }
        return response.data();
    }

    private <T> BookingPaymentApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.NotFound exception) {
            throw new NotFoundException("Payment not found");
        } catch (FeignException.BadRequest exception) {
            throw new BadRequestException(extractMessage(exception));
        } catch (FeignException exception) {
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
}
