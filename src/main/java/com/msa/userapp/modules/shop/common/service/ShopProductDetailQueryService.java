package com.msa.userapp.modules.shop.common.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.shop.common.dto.ProductDetailResponse;
import com.msa.userapp.modules.shop.common.dto.ProductImageResponse;
import com.msa.userapp.modules.shop.common.dto.ProductOptionGroupResponse;
import com.msa.userapp.modules.shop.common.dto.ProductOptionResponse;
import com.msa.userapp.modules.shop.common.dto.ProductVariantResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopProductDetailQueryService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ShopProductDetailQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProductDetailResponse findProductDetail(Long productId, Long variantId) {
        ProductBaseRow product = loadProduct(productId);
        List<ProductImageResponse> images = loadImages(productId);
        List<ProductVariantResponse> variants = loadVariants(productId);
        Long selectedVariantId = selectVariantId(variants, variantId);
        List<ProductOptionGroupResponse> optionGroups = loadOptionGroups(productId);

        return new ProductDetailResponse(
                product.productId(),
                selectedVariantId,
                product.shopId(),
                product.shopTypeId(),
                product.categoryId(),
                product.productName(),
                product.shopName(),
                product.categoryName(),
                product.brandName(),
                product.description(),
                product.shortDescription(),
                product.productType(),
                product.attributesJson(),
                product.avgRating(),
                product.totalReviews(),
                product.totalOrders(),
                product.outOfStock(),
                images,
                variants,
                optionGroups
        );
    }

    private ProductBaseRow loadProduct(Long productId) {
        String sql = """
                SELECT
                    p.id AS product_id,
                    s.id AS shop_id,
                    s.shop_type_id,
                    sc.id AS category_id,
                    p.name AS product_name,
                    s.shop_name,
                    sc.name AS category_name,
                    p.brand_name,
                    p.description,
                    p.short_description,
                    p.product_type,
                    CAST(p.attributes_json AS CHAR) AS attributes_json,
                    p.avg_rating,
                    p.total_reviews,
                    p.total_orders,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM product_variants pv
                            LEFT JOIN inventory i ON i.variant_id = pv.id
                            WHERE pv.product_id = p.id
                              AND pv.is_active = 1
                              AND (
                                   COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                                   OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                              )
                        )
                        AND NOT EXISTS (
                            SELECT 1
                            FROM product_variants pv
                            LEFT JOIN inventory i ON i.variant_id = pv.id
                            WHERE pv.product_id = p.id
                              AND pv.is_active = 1
                              AND COALESCE(i.inventory_status, 'IN_STOCK') <> 'OUT_OF_STOCK'
                              AND COALESCE(i.quantity_available, 0) > COALESCE(i.reserved_quantity, 0)
                        )
                        THEN 1 ELSE 0
                    END AS out_of_stock
                FROM products p
                INNER JOIN shops s ON s.id = p.shop_id
                INNER JOIN shop_categories sc ON sc.id = p.shop_category_id
                WHERE p.id = :productId
                  AND p.is_active = 1
                  AND s.approval_status = 'APPROVED'
                """;
        List<ProductBaseRow> rows = jdbcTemplate.query(sql, Map.of("productId", productId), (rs, rowNum) ->
                new ProductBaseRow(
                        rs.getLong("product_id"),
                        rs.getLong("shop_id"),
                        nullableLong(rs, "shop_type_id"),
                        rs.getLong("category_id"),
                        rs.getString("product_name"),
                        rs.getString("shop_name"),
                        rs.getString("category_name"),
                        rs.getString("brand_name"),
                        rs.getString("description"),
                        rs.getString("short_description"),
                        rs.getString("product_type"),
                        rs.getString("attributes_json"),
                        valueOrZero(rs.getBigDecimal("avg_rating")),
                        rs.getLong("total_reviews"),
                        rs.getLong("total_orders"),
                        rs.getBoolean("out_of_stock")
                ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Product not found");
        }
        return rows.getFirst();
    }

    private List<ProductImageResponse> loadImages(Long productId) {
        String sql = """
                SELECT
                    pi.id,
                    file.object_key,
                    pi.image_role,
                    pi.sort_order,
                    pi.is_primary
                FROM product_images pi
                INNER JOIN files file ON file.id = pi.file_id
                WHERE pi.product_id = :productId
                ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC
                """;
        return jdbcTemplate.query(sql, Map.of("productId", productId), (rs, rowNum) -> new ProductImageResponse(
                rs.getLong("id"),
                rs.getString("object_key"),
                rs.getString("image_role"),
                rs.getInt("sort_order"),
                rs.getBoolean("is_primary")
        ));
    }

    private List<ProductVariantResponse> loadVariants(Long productId) {
        String sql = """
                SELECT
                    pv.id,
                    pv.variant_name,
                    pv.mrp,
                    pv.selling_price,
                    pv.is_default,
                    pv.is_active,
                    CAST(pv.attributes_json AS CHAR) AS attributes_json,
                    COALESCE(i.inventory_status, 'OUT_OF_STOCK') AS inventory_status,
                    CASE
                        WHEN COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                          OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                        THEN 1 ELSE 0
                    END AS out_of_stock
                FROM product_variants pv
                LEFT JOIN inventory i ON i.variant_id = pv.id
                WHERE pv.product_id = :productId
                ORDER BY pv.is_default DESC, pv.sort_order ASC, pv.id ASC
                """;
        return jdbcTemplate.query(sql, Map.of("productId", productId), (rs, rowNum) -> new ProductVariantResponse(
                rs.getLong("id"),
                rs.getString("variant_name"),
                rs.getBigDecimal("mrp"),
                rs.getBigDecimal("selling_price"),
                rs.getBoolean("is_default"),
                rs.getBoolean("is_active"),
                rs.getString("attributes_json"),
                rs.getString("inventory_status"),
                rs.getBoolean("out_of_stock")
        ));
    }

    private List<ProductOptionGroupResponse> loadOptionGroups(Long productId) {
        String sql = """
                SELECT
                    pog.id AS group_id,
                    pog.group_name,
                    pog.group_type,
                    pog.min_select,
                    pog.max_select,
                    pog.is_required,
                    po.id AS option_id,
                    po.option_name,
                    po.price_delta,
                    po.is_default
                FROM product_option_groups pog
                LEFT JOIN product_options po
                  ON po.option_group_id = pog.id
                 AND po.is_active = 1
                WHERE pog.product_id = :productId
                  AND pog.is_active = 1
                ORDER BY pog.sort_order ASC, pog.id ASC, po.sort_order ASC, po.id ASC
                """;

        Map<Long, ProductOptionGroupBuilder> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(sql, Map.of("productId", productId), rs -> {
            long groupId = rs.getLong("group_id");
            ProductOptionGroupBuilder builder = grouped.get(groupId);
            if (builder == null) {
                builder = new ProductOptionGroupBuilder(
                        groupId,
                        rs.getString("group_name"),
                        rs.getString("group_type"),
                        rs.getInt("min_select"),
                        rs.getInt("max_select"),
                        rs.getBoolean("is_required")
                );
                grouped.put(groupId, builder);
            }
            long optionId = rs.getLong("option_id");
            if (!rs.wasNull()) {
                builder.options().add(new ProductOptionResponse(
                        optionId,
                        rs.getString("option_name"),
                        rs.getBigDecimal("price_delta"),
                        rs.getBoolean("is_default")
                ));
            }
        });
        return grouped.values().stream().map(ProductOptionGroupBuilder::build).toList();
    }

    private static Long selectVariantId(List<ProductVariantResponse> variants, Long requestedVariantId) {
        if (variants.isEmpty()) {
            return null;
        }
        if (requestedVariantId != null && variants.stream().anyMatch(variant -> variant.id().equals(requestedVariantId))) {
            return requestedVariantId;
        }
        return variants.stream()
                .filter(ProductVariantResponse::defaultVariant)
                .map(ProductVariantResponse::id)
                .findFirst()
                .orElse(variants.getFirst().id());
    }

    private static Long nullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ProductBaseRow(
            Long productId,
            Long shopId,
            Long shopTypeId,
            Long categoryId,
            String productName,
            String shopName,
            String categoryName,
            String brandName,
            String description,
            String shortDescription,
            String productType,
            String attributesJson,
            BigDecimal avgRating,
            long totalReviews,
            long totalOrders,
            boolean outOfStock
    ) {
    }

    private record ProductOptionGroupBuilder(
            Long groupId,
            String groupName,
            String groupType,
            int minSelect,
            int maxSelect,
            boolean required,
            List<ProductOptionResponse> options
    ) {
        private ProductOptionGroupBuilder(Long groupId, String groupName, String groupType, int minSelect, int maxSelect, boolean required) {
            this(groupId, groupName, groupType, minSelect, maxSelect, required, new ArrayList<>());
        }

        private ProductOptionGroupResponse build() {
            return new ProductOptionGroupResponse(groupId, groupName, groupType, minSelect, maxSelect, required, List.copyOf(options));
        }
    }
}
