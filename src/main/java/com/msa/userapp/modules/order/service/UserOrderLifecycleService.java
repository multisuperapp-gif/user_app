package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.bookingpayment.BookingPaymentOrderClient;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentApiResponse;
import com.msa.userapp.integration.bookingpayment.dto.BookingPaymentOrderDtos;
import com.msa.userapp.modules.order.dto.CancelOrderRequest;
import feign.FeignException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOrderLifecycleService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BookingPaymentOrderClient bookingPaymentOrderClient;

    public UserOrderLifecycleService(
            NamedParameterJdbcTemplate jdbcTemplate,
            BookingPaymentOrderClient bookingPaymentOrderClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.bookingPaymentOrderClient = bookingPaymentOrderClient;
    }

    @Transactional
    public void cancel(String authorizationHeader, Long userId, Long orderId, CancelOrderRequest request) {
        validateOwnership(userId, orderId);
        try {
            BookingPaymentApiResponse<BookingPaymentOrderDtos.ShopOrderData> response =
                    bookingPaymentOrderClient.cancelByUser(
                            authorizationHeader,
                            userId,
                            new BookingPaymentOrderDtos.CancelShopOrderRequest(orderId, userId, request == null ? null : request.reason())
                    );
            if (response == null || !response.success()) {
                throw new BadRequestException(
                        response == null || response.message() == null || response.message().isBlank()
                                ? "Order cancellation failed"
                                : response.message()
                );
            }
        } catch (FeignException.NotFound exception) {
            throw new NotFoundException("Order not found");
        } catch (FeignException.BadRequest exception) {
            String content = exception.contentUTF8();
            throw new BadRequestException(content == null || content.isBlank() ? "Order cancellation failed" : content);
        } catch (FeignException exception) {
            throw new BadRequestException("Order service is unavailable right now");
        }
    }

    private void validateOwnership(Long userId, Long orderId) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM orders
                WHERE id = :orderId
                  AND user_id = :userId
                LIMIT 1
                """, Map.of("orderId", orderId, "userId", userId), (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new NotFoundException("Order not found");
        }
    }
}
