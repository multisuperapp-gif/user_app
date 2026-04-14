package com.msa.userapp.modules.order.dto;

public record PlaceOrderRequest(
        Long addressId,
        String fulfillmentType
) {
}
