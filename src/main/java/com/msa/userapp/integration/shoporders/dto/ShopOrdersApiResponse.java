package com.msa.userapp.integration.shoporders.dto;

public record ShopOrdersApiResponse<T>(
        boolean success,
        String message,
        String errorCode,
        T data
) {
}
