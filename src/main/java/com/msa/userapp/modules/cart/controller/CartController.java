package com.msa.userapp.modules.cart.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.cart.dto.CartAddItemRequest;
import com.msa.userapp.modules.cart.dto.CartResponse;
import com.msa.userapp.modules.cart.dto.CartUpdateItemRequest;
import com.msa.userapp.modules.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> activeCart(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(cartService.getActiveCart(userId));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartAddItemRequest request
    ) {
        return ApiResponse.success("Item added to cart", cartService.addItem(userId, request));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartResponse> updateItem(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartUpdateItemRequest request
    ) {
        return ApiResponse.success("Cart item updated", cartService.updateItem(userId, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> removeItem(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long itemId
    ) {
        return ApiResponse.success("Cart item removed", cartService.removeItem(userId, itemId));
    }
}
