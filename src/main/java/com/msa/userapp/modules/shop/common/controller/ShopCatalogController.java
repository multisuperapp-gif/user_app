package com.msa.userapp.modules.shop.common.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.shop.common.dto.HomeBootstrapResponse;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.modules.shop.common.dto.ProductDetailResponse;
import com.msa.userapp.modules.shop.common.dto.ShopCategoryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProductCardResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeResponse;
import com.msa.userapp.modules.shop.common.service.ShopCatalogGatewayService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class ShopCatalogController {
    private final ShopCatalogGatewayService shopCatalogGatewayService;

    public ShopCatalogController(ShopCatalogGatewayService shopCatalogGatewayService) {
        this.shopCatalogGatewayService = shopCatalogGatewayService;
    }

    @GetMapping("/home/bootstrap")
    public ApiResponse<HomeBootstrapResponse> homeBootstrap(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.homeBootstrap(latitude, longitude, page, size));
    }

    @GetMapping("/shop/types")
    public ApiResponse<List<ShopTypeResponse>> shopTypes() {
        return ApiResponse.ok(shopCatalogGatewayService.shopTypes());
    }

    @GetMapping("/shop/categories")
    public ApiResponse<List<ShopCategoryResponse>> shopCategories(
            @RequestParam(required = false) Long shopTypeId,
            @RequestParam(required = false) Long parentCategoryId
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.shopCategories(shopTypeId, parentCategoryId));
    }

    @GetMapping("/shop/products")
    public ApiResponse<PageResponse<ShopProductCardResponse>> shopProducts(
            @RequestParam(required = false) Long shopTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.shopProducts(
                shopTypeId,
                categoryId,
                search,
                latitude,
                longitude,
                page,
                size
        ));
    }

    @GetMapping("/shop/products/{productId}")
    public ApiResponse<ProductDetailResponse> productDetail(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId
    ) {
        return ApiResponse.ok(shopCatalogGatewayService.productDetail(productId, variantId));
    }
}
