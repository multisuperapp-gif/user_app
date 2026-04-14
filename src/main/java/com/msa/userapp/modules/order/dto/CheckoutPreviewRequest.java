package com.msa.userapp.modules.order.dto;

public record CheckoutPreviewRequest(
        Long addressId,
        String fulfillmentType
) {
}
