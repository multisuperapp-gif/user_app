package com.msa.userapp.modules.shop.common.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.integration.shoporders.ShopOrdersClient;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersApiResponse;
import com.msa.userapp.integration.shoporders.dto.ShopOrdersDtos;
import com.msa.userapp.modules.shop.common.dto.HomeBootstrapResponse;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.modules.shop.common.dto.ProductDetailResponse;
import com.msa.userapp.modules.shop.common.dto.ProductImageResponse;
import com.msa.userapp.modules.shop.common.dto.ProductOptionGroupResponse;
import com.msa.userapp.modules.shop.common.dto.ProductOptionResponse;
import com.msa.userapp.modules.shop.common.dto.ProductVariantResponse;
import com.msa.userapp.modules.shop.common.dto.ShopCategoryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProductCardResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProfileResponse;
import com.msa.userapp.modules.shop.common.dto.ShopSummaryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeLandingResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeResponse;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShopCatalogGatewayService {
    private final ShopOrdersClient shopOrdersClient;

    public ShopCatalogGatewayService(ShopOrdersClient shopOrdersClient) {
        this.shopOrdersClient = shopOrdersClient;
    }

    public HomeBootstrapResponse homeBootstrap(Double latitude, Double longitude, int page, int size) {
        ShopOrdersDtos.PublicHomeBootstrapData data = requireSuccess(call(() -> shopOrdersClient.publicHomeBootstrap(latitude, longitude, page, size)));
        return new HomeBootstrapResponse(
                mapShopTypes(data.shopTypes()),
                mapProductPage(data.featuredProducts())
        );
    }

    public List<ShopTypeResponse> shopTypes() {
        return mapShopTypes(requireSuccess(call(shopOrdersClient::publicShopTypes)));
    }

    public List<ShopCategoryResponse> shopCategories(Long shopTypeId, Long parentCategoryId) {
        return mapCategories(requireSuccess(call(() -> shopOrdersClient.publicShopCategories(shopTypeId, parentCategoryId))));
    }

    public PageResponse<ShopProductCardResponse> shopProducts(
            Long shopTypeId,
            Long categoryId,
            String search,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        return mapProductPage(requireSuccess(call(() -> shopOrdersClient.publicShopProducts(
                shopTypeId,
                categoryId,
                search,
                latitude,
                longitude,
                page,
                size
        ))));
    }

    public ProductDetailResponse productDetail(Long productId, Long variantId) {
        ShopOrdersDtos.PublicProductDetailData data = requireSuccess(call(() -> shopOrdersClient.publicProductDetail(productId, variantId)));
        return new ProductDetailResponse(
                data.productId(),
                data.selectedVariantId(),
                data.shopId(),
                data.shopTypeId(),
                data.categoryId(),
                data.productName(),
                data.shopName(),
                data.categoryName(),
                data.brandName(),
                data.description(),
                data.shortDescription(),
                data.productType(),
                data.attributesJson(),
                defaultAmount(data.avgRating()),
                data.totalReviews(),
                data.totalOrders(),
                data.outOfStock(),
                data.images() == null ? List.of() : data.images().stream()
                        .map(image -> new ProductImageResponse(
                                image.id(),
                                image.objectKey(),
                                image.imageRole(),
                                image.sortOrder(),
                                image.primaryImage()
                        ))
                        .toList(),
                data.variants() == null ? List.of() : data.variants().stream()
                        .map(variant -> new ProductVariantResponse(
                                variant.id(),
                                variant.variantName(),
                                variant.mrp(),
                                variant.sellingPrice(),
                                variant.defaultVariant(),
                                variant.active(),
                                variant.attributesJson(),
                                variant.inventoryStatus(),
                                variant.outOfStock()
                        ))
                        .toList(),
                data.optionGroups() == null ? List.of() : data.optionGroups().stream()
                        .map(group -> new ProductOptionGroupResponse(
                                group.id(),
                                group.groupName(),
                                group.groupType(),
                                group.minSelect(),
                                group.maxSelect(),
                                group.required(),
                                group.options() == null ? List.of() : group.options().stream()
                                        .map(option -> new ProductOptionResponse(
                                                option.id(),
                                                option.optionName(),
                                                option.priceDelta(),
                                                option.defaultOption()
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    public ShopTypeLandingResponse landing(String normalizedShopType, Double latitude, Double longitude, int page, int size) {
        ShopOrdersDtos.PublicShopTypeLandingData data = requireSuccess(call(() -> shopOrdersClient.publicTypeLanding(
                normalizedShopType,
                latitude,
                longitude,
                page,
                size
        )));
        return new ShopTypeLandingResponse(
                mapShopType(data.shopType()),
                mapCategories(data.categories()),
                mapProductPage(data.products()),
                mapShopPage(data.shops())
        );
    }

    public List<ShopCategoryResponse> categories(String normalizedShopType, Long parentCategoryId) {
        return mapCategories(requireSuccess(call(() -> shopOrdersClient.publicTypeCategories(normalizedShopType, parentCategoryId))));
    }

    public PageResponse<ShopProductCardResponse> products(String normalizedShopType, Long categoryId, String search, int page, int size) {
        return mapProductPage(requireSuccess(call(() -> shopOrdersClient.publicTypeProducts(
                normalizedShopType,
                categoryId,
                search,
                page,
                size
        ))));
    }

    public PageResponse<ShopSummaryResponse> shops(
            String normalizedShopType,
            String search,
            int page,
            int size
    ) {
        return shops(normalizedShopType, search, null, null, page, size);
    }

    public PageResponse<ShopSummaryResponse> shops(
            String normalizedShopType,
            String search,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        return mapShopPage(requireSuccess(call(() -> shopOrdersClient.publicTypeShops(
                normalizedShopType,
                search,
                latitude,
                longitude,
                page,
                size
        ))));
    }

    public ShopProfileResponse shopProfile(
            String normalizedShopType,
            Long shopId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        ShopOrdersDtos.PublicShopProfileData data = requireSuccess(call(() -> shopOrdersClient.publicShopProfile(
                normalizedShopType,
                shopId,
                categoryId,
                search,
                page,
                size
        )));
        return new ShopProfileResponse(
                mapShopSummary(data.shop()),
                mapCategories(data.categories()),
                mapProductPage(data.products())
        );
    }

    private List<ShopTypeResponse> mapShopTypes(List<ShopOrdersDtos.PublicShopTypeData> rows) {
        return rows == null ? List.of() : rows.stream().map(this::mapShopType).toList();
    }

    private ShopTypeResponse mapShopType(ShopOrdersDtos.PublicShopTypeData data) {
        return new ShopTypeResponse(
                data.id(),
                data.name(),
                data.normalizedName(),
                data.themeColor(),
                data.comingSoon(),
                data.comingSoonMessage(),
                data.iconObjectKey(),
                data.bannerObjectKey(),
                data.sortOrder()
        );
    }

    private List<ShopCategoryResponse> mapCategories(List<ShopOrdersDtos.PublicShopCategoryData> rows) {
        return rows == null ? List.of() : rows.stream()
                .map(row -> new ShopCategoryResponse(
                        row.id(),
                        row.parentCategoryId(),
                        row.shopTypeId(),
                        row.name(),
                        row.normalizedName(),
                        row.themeColor(),
                        row.comingSoon(),
                        row.comingSoonMessage(),
                        row.imageObjectKey(),
                        row.sortOrder()
                ))
                .toList();
    }

    private PageResponse<ShopProductCardResponse> mapProductPage(ShopOrdersDtos.PublicPageResponse<ShopOrdersDtos.PublicShopProductCardData> page) {
        if (page == null) {
            return new PageResponse<>(List.of(), 0, 20, false);
        }
        return new PageResponse<>(
                page.items() == null ? List.of() : page.items().stream()
                        .map(row -> new ShopProductCardResponse(
                                row.productId(),
                                row.variantId(),
                                row.shopId(),
                                row.shopTypeId(),
                                row.categoryId(),
                                row.productName(),
                                row.shopName(),
                                row.categoryName(),
                                row.brandName(),
                                row.shortDescription(),
                                row.productType(),
                                row.mrp(),
                                row.sellingPrice(),
                                defaultAmount(row.avgRating()),
                                row.totalReviews(),
                                row.totalOrders(),
                                row.inventoryStatus(),
                                row.outOfStock(),
                                row.promotionScore(),
                                row.imageObjectKey()
                        ))
                        .toList(),
                page.page(),
                page.size(),
                page.hasMore()
        );
    }

    private PageResponse<ShopSummaryResponse> mapShopPage(ShopOrdersDtos.PublicPageResponse<ShopOrdersDtos.PublicShopSummaryData> page) {
        if (page == null) {
            return new PageResponse<>(List.of(), 0, 20, false);
        }
        return new PageResponse<>(
                page.items() == null ? List.of() : page.items().stream().map(this::mapShopSummary).toList(),
                page.page(),
                page.size(),
                page.hasMore()
        );
    }

    private ShopSummaryResponse mapShopSummary(ShopOrdersDtos.PublicShopSummaryData row) {
        return new ShopSummaryResponse(
                row.shopId(),
                row.shopTypeId(),
                row.shopName(),
                row.shopCode(),
                row.logoObjectKey(),
                row.coverObjectKey(),
                defaultAmount(row.avgRating()),
                row.totalReviews(),
                row.city(),
                row.latitude(),
                row.longitude(),
                row.deliveryType(),
                row.deliveryRadiusKm(),
                row.minOrderAmount(),
                row.deliveryFee(),
                row.openNow(),
                row.closingSoon(),
                row.acceptsOrders(),
                row.closesAt()
        );
    }

    private <T> ShopOrdersApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.BadRequest exception) {
            log.warn("Shop catalog service rejected request status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new BadRequestException(extractMessage(exception));
        } catch (FeignException.NotFound exception) {
            log.debug("Shop catalog service returned not found status={} message={}",
                    exception.status(),
                    extractMessage(exception));
            throw new com.msa.userapp.common.exception.NotFoundException(extractMessage(exception));
        } catch (FeignException exception) {
            log.error("Shop catalog service call failed status={}", exception.status(), exception);
            throw new BadRequestException("Shop catalog service is unavailable right now");
        }
    }

    private <T> T requireSuccess(ShopOrdersApiResponse<T> response) {
        if (response == null || !response.success()) {
            log.warn("Shop catalog service returned unsuccessful response message={}",
                    response == null ? "null response" : response.message());
            throw new BadRequestException(response == null || response.message() == null || response.message().isBlank()
                    ? "Shop catalog request failed"
                    : response.message());
        }
        return response.data();
    }

    private String extractMessage(FeignException exception) {
        String content = exception.contentUTF8();
        return content == null || content.isBlank() ? "Shop catalog request failed" : content;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @FunctionalInterface
    private interface FeignCall<T> {
        ShopOrdersApiResponse<T> execute();
    }
}
