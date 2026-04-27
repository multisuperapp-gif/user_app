package com.msa.userapp.modules.shop.grocery.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.modules.shop.common.dto.ShopCategoryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProductCardResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProfileResponse;
import com.msa.userapp.modules.shop.common.dto.ShopSummaryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeLandingResponse;
import com.msa.userapp.modules.shop.common.service.ShopCatalogGatewayService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/grocery")
public class GroceryCatalogController {
    private static final String SHOP_TYPE = "grocery";

    private final ShopCatalogGatewayService shopCatalogGatewayService;

    public GroceryCatalogController(ShopCatalogGatewayService shopCatalogGatewayService) {
        this.shopCatalogGatewayService = shopCatalogGatewayService;
    }

    @GetMapping("/landing")
    public ApiResponse<ShopTypeLandingResponse> landing(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.landing(SHOP_TYPE, latitude, longitude, page, size));
    }

    @GetMapping("/categories")
    public ApiResponse<List<ShopCategoryResponse>> categories(@RequestParam(required = false) Long parentCategoryId) {
        return ApiResponse.ok(shopCatalogGatewayService.categories(SHOP_TYPE, parentCategoryId));
    }

    @GetMapping("/products")
    public ApiResponse<PageResponse<ShopProductCardResponse>> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.products(SHOP_TYPE, categoryId, search, page, size));
    }

    @GetMapping("/shops")
    public ApiResponse<PageResponse<ShopSummaryResponse>> shops(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.shops(SHOP_TYPE, search, page, size));
    }

    @GetMapping("/shops/{shopId}")
    public ApiResponse<ShopProfileResponse> shopProfile(
            @PathVariable Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.shopProfile(SHOP_TYPE, shopId, categoryId, search, page, size));
    }
}
