package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.shoporders.ShopOrdersClient;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersApiResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos;
import com.msa.userapp.modules.order.dto.CancelOrderRequest;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserOrderLifecycleService {
    private final ShopOrdersClient shopOrdersClient;

    public UserOrderLifecycleService(
            ShopOrdersClient shopOrdersClient
    ) {
        this.shopOrdersClient = shopOrdersClient;
    }

    public void cancel(String authorizationHeader, Long userId, Long orderId, CancelOrderRequest request) {
        try {
            ShopOrdersApiResponse<Void> response =
                    shopOrdersClient.cancelOrder(
                            authorizationHeader,
                            userId,
                            orderId,
                            new ShopOrdersDtos.ConsumerCancelOrderRequest(request == null ? null : request.reason())
                    );
            if (response == null || !response.success()) {
                log.warn("Shop order cancellation returned unsuccessful response userId={} orderId={} message={}",
                        userId,
                        orderId,
                        response == null ? "null response" : response.message());
                throw new BadRequestException(
                        response == null || response.message() == null || response.message().isBlank()
                                ? "Order cancellation failed"
                                : response.message()
                );
            }
            log.info("Cancelled shop order userId={} orderId={}", userId, orderId);
        } catch (FeignException.NotFound exception) {
            log.debug("Shop order cancellation returned not found userId={} orderId={} status={}",
                    userId,
                    orderId,
                    exception.status());
            throw new NotFoundException("Order not found");
        } catch (FeignException.BadRequest exception) {
            String content = exception.contentUTF8();
            log.warn("Shop order cancellation rejected userId={} orderId={} status={} message={}",
                    userId,
                    orderId,
                    exception.status(),
                    content);
            throw new BadRequestException(content == null || content.isBlank() ? "Order cancellation failed" : content);
        } catch (FeignException exception) {
            log.error("Shop order cancellation failed userId={} orderId={} status={}",
                    userId,
                    orderId,
                    exception.status(),
                    exception);
            throw new BadRequestException("Order service is unavailable right now");
        }
    }
}
