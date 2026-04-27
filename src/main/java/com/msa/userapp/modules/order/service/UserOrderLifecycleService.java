package com.msa.userapp.modules.order.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.integration.shoporders.ShopOrdersClient;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersApiResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos;
import com.msa.userapp.modules.order.dto.CancelOrderRequest;
import feign.FeignException;
import org.springframework.stereotype.Service;

@Service
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
}
