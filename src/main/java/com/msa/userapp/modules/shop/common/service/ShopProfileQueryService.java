package com.msa.userapp.modules.shop.common.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.modules.shop.common.dto.ShopCategoryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProductCardResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProfileResponse;
import com.msa.userapp.modules.shop.common.dto.ShopSummaryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeLandingResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ShopProfileQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ShopCatalogQueryService shopCatalogQueryService;

    public ShopProfileQueryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ShopCatalogQueryService shopCatalogQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.shopCatalogQueryService = shopCatalogQueryService;
    }

    public ShopTypeLandingResponse landing(String normalizedShopType, int page, int size) {
        return landing(normalizedShopType, null, null, page, size);
    }

    public ShopTypeLandingResponse landing(
            String normalizedShopType,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        ShopTypeResponse shopType = requireShopType(normalizedShopType);
        return new ShopTypeLandingResponse(
                shopType,
                shopCatalogQueryService.findCategories(shopType.id(), null),
                shopCatalogQueryService.findProducts(shopType.id(), null, null, null, null, page, size),
                findShops(shopType.id(), null, null, latitude, longitude, page, size)
        );
    }

    public List<ShopCategoryResponse> categories(String normalizedShopType, Long parentCategoryId) {
        ShopTypeResponse shopType = requireShopType(normalizedShopType);
        return shopCatalogQueryService.findCategories(shopType.id(), parentCategoryId);
    }

    public PageResponse<ShopProductCardResponse> products(
            String normalizedShopType,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        ShopTypeResponse shopType = requireShopType(normalizedShopType);
        return shopCatalogQueryService.findProducts(shopType.id(), categoryId, search, null, null, page, size);
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
        ShopTypeResponse shopType = requireShopType(normalizedShopType);
        return findShops(shopType.id(), null, search, latitude, longitude, page, size);
    }

    public ShopProfileResponse shopProfile(
            String normalizedShopType,
            Long shopId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        ShopTypeResponse shopType = requireShopType(normalizedShopType);
        ShopSummaryResponse shop = requireShop(shopType.id(), shopId);
        return new ShopProfileResponse(
                shop,
                findShopCategories(shopId),
                findProductsByShop(shopId, categoryId, search, page, size)
        );
    }

    private ShopTypeResponse requireShopType(String normalizedShopType) {
        List<ShopTypeResponse> rows = jdbcTemplate.query("""
                SELECT
                    st.id,
                    COALESCE(st.display_label, st.name) AS name,
                    st.normalized_name,
                    st.theme_color,
                    st.is_coming_soon,
                    st.coming_soon_message,
                    icon_file.object_key AS icon_object_key,
                    banner_file.object_key AS banner_object_key,
                    st.sort_order
                FROM shop_types st
                LEFT JOIN files icon_file ON icon_file.id = st.icon_file_id
                LEFT JOIN files banner_file ON banner_file.id = st.banner_file_id
                WHERE st.is_active = 1
                  AND st.normalized_name = :normalizedName
                LIMIT 1
                """, Map.of("normalizedName", normalizedShopType), (rs, rowNum) -> new ShopTypeResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("theme_color"),
                rs.getBoolean("is_coming_soon"),
                rs.getString("coming_soon_message"),
                rs.getString("icon_object_key"),
                rs.getString("banner_object_key"),
                rs.getInt("sort_order")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Shop type not found");
        }
        return rows.getFirst();
    }

    private PageResponse<ShopSummaryResponse> findShops(
            Long shopTypeId,
            Long categoryId,
            String search,
            Double userLatitude,
            Double userLongitude,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("shopTypeId", shopTypeId);
        params.put("categoryId", categoryId);
        params.put("search", StringUtils.hasText(search) ? "%" + search.trim() + "%" : null);
        params.put("userLatitude", userLatitude);
        params.put("userLongitude", userLongitude);
        params.put("limit", limit);
        params.put("offset", offset);

        String sql = """
                SELECT
                    s.id AS shop_id,
                    s.shop_type_id,
                    s.shop_name,
                    s.shop_code,
                    s.avg_rating,
                    s.total_reviews,
                    address.city,
                    sl.latitude,
                    sl.longitude,
                    CASE
                        WHEN :userLatitude IS NOT NULL AND :userLongitude IS NOT NULL THEN
                            6371 * ACOS(
                                LEAST(
                                    1,
                                    COS(RADIANS(:userLatitude)) * COS(RADIANS(sl.latitude))
                                    * COS(RADIANS(sl.longitude) - RADIANS(:userLongitude))
                                    + SIN(RADIANS(:userLatitude)) * SIN(RADIANS(sl.latitude))
                                )
                            )
                        ELSE NULL
                    END AS distance_km,
                    sdr.delivery_type,
                    sdr.radius_km,
                    sdr.min_order_amount,
                    sdr.delivery_fee,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time THEN 1
                        ELSE 0
                    END AS open_now,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time
                         AND CURRENT_TIME() >= SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.closing_soon_minutes, 60) * 60))
                        THEN 1
                        ELSE 0
                    END AS closing_soon,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.order_cutoff_minutes_before_close, 30) * 60))
                        THEN 1
                        ELSE 0
                    END AS accepts_orders,
                    DATE_FORMAT(soh.close_time, '%H:%i') AS closes_at
                FROM shops s
                INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
                INNER JOIN user_addresses address ON address.id = sl.address_id
                LEFT JOIN shop_delivery_rules sdr ON sdr.shop_location_id = sl.id
                LEFT JOIN shop_operating_hours soh
                  ON soh.shop_location_id = sl.id
                 AND soh.weekday = WEEKDAY(CURRENT_DATE())
                WHERE s.approval_status = 'APPROVED'
                  AND s.shop_type_id = :shopTypeId
                  AND s.shop_name IS NOT NULL
                  AND (
                        :categoryId IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM products p
                            WHERE p.shop_id = s.id
                              AND p.shop_category_id = :categoryId
                              AND p.is_active = 1
                        )
                  )
                  AND (
                        :search IS NULL
                        OR s.shop_name LIKE :search
                        OR address.city LIKE :search
                  )
                ORDER BY
                  accepts_orders DESC,
                  closing_soon ASC,
                  distance_km ASC,
                  s.avg_rating DESC,
                  s.total_reviews DESC,
                  s.updated_at DESC
                LIMIT :limit OFFSET :offset
                """;

        List<ShopSummaryResponse> rows = jdbcTemplate.query(sql, params, shopSummaryMapper());
        boolean hasMore = rows.size() > safeSize;
        List<ShopSummaryResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    private ShopSummaryResponse requireShop(Long shopTypeId, Long shopId) {
        List<ShopSummaryResponse> rows = jdbcTemplate.query("""
                SELECT
                    s.id AS shop_id,
                    s.shop_type_id,
                    s.shop_name,
                    s.shop_code,
                    s.avg_rating,
                    s.total_reviews,
                    address.city,
                    sl.latitude,
                    sl.longitude,
                    sdr.delivery_type,
                    sdr.radius_km,
                    sdr.min_order_amount,
                    sdr.delivery_fee,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time THEN 1
                        ELSE 0
                    END AS open_now,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time
                         AND CURRENT_TIME() >= SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.closing_soon_minutes, 60) * 60))
                        THEN 1
                        ELSE 0
                    END AS closing_soon,
                    CASE
                        WHEN soh.is_closed = 1 THEN 0
                        WHEN CURRENT_TIME() BETWEEN soh.open_time AND SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.order_cutoff_minutes_before_close, 30) * 60))
                        THEN 1
                        ELSE 0
                    END AS accepts_orders,
                    DATE_FORMAT(soh.close_time, '%H:%i') AS closes_at
                FROM shops s
                INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
                INNER JOIN user_addresses address ON address.id = sl.address_id
                LEFT JOIN shop_delivery_rules sdr ON sdr.shop_location_id = sl.id
                LEFT JOIN shop_operating_hours soh
                  ON soh.shop_location_id = sl.id
                 AND soh.weekday = WEEKDAY(CURRENT_DATE())
                WHERE s.id = :shopId
                  AND s.shop_type_id = :shopTypeId
                  AND s.approval_status = 'APPROVED'
                LIMIT 1
                """, Map.of("shopId", shopId, "shopTypeId", shopTypeId), shopSummaryMapper());
        if (rows.isEmpty()) {
            throw new NotFoundException("Shop not found");
        }
        return rows.getFirst();
    }

    private List<ShopCategoryResponse> findShopCategories(Long shopId) {
        return jdbcTemplate.query("""
                SELECT
                    sc.id,
                    sc.parent_category_id,
                    s.shop_type_id,
                    COALESCE(sc.display_label, sc.name) AS name,
                    sc.normalized_name,
                    sc.theme_color,
                    0 AS is_coming_soon,
                    NULL AS coming_soon_message,
                    image_file.object_key AS image_object_key,
                    sc.sort_order
                FROM shop_inventory_categories sic
                INNER JOIN shop_categories sc ON sc.id = sic.shop_category_id
                INNER JOIN shops s ON s.id = sic.shop_id
                LEFT JOIN files image_file ON image_file.id = sc.image_file_id
                WHERE sic.shop_id = :shopId
                  AND sic.is_enabled = 1
                  AND sc.is_active = 1
                ORDER BY sc.sort_order ASC, sc.name ASC
                """, Map.of("shopId", shopId), (rs, rowNum) -> new ShopCategoryResponse(
                rs.getLong("id"),
                nullableLong(rs, "parent_category_id"),
                nullableLong(rs, "shop_type_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("theme_color"),
                rs.getBoolean("is_coming_soon"),
                rs.getString("coming_soon_message"),
                rs.getString("image_object_key"),
                rs.getInt("sort_order")
        ));
    }

    private PageResponse<ShopProductCardResponse> findProductsByShop(
            Long shopId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("shopId", shopId);
        params.put("categoryId", categoryId);
        params.put("search", StringUtils.hasText(search) ? "%" + search.trim() + "%" : null);
        params.put("limit", limit);
        params.put("offset", offset);

        String sql = """
                SELECT
                    p.id AS product_id,
                    pv.id AS variant_id,
                    s.id AS shop_id,
                    s.shop_type_id,
                    sc.id AS category_id,
                    p.name AS product_name,
                    s.shop_name,
                    sc.name AS category_name,
                    p.brand_name,
                    p.short_description,
                    p.product_type,
                    pv.mrp,
                    pv.selling_price,
                    p.avg_rating,
                    p.total_reviews,
                    p.total_orders,
                    COALESCE(i.inventory_status, 'OUT_OF_STOCK') AS inventory_status,
                    CASE
                        WHEN COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                          OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                        THEN 1 ELSE 0
                    END AS out_of_stock,
                    COALESCE((
                        SELECT MAX(pp.priority_score)
                        FROM product_promotions pp
                        WHERE pp.product_id = p.id
                          AND pp.status = 'ACTIVE'
                          AND CURRENT_TIMESTAMP BETWEEN pp.starts_at AND pp.ends_at
                    ), 0) AS promotion_score,
                    image_file.object_key AS image_object_key
                FROM products p
                INNER JOIN shops s ON s.id = p.shop_id
                INNER JOIN shop_categories sc ON sc.id = p.shop_category_id
                LEFT JOIN product_variants pv
                  ON pv.product_id = p.id
                 AND pv.is_active = 1
                 AND pv.is_default = 1
                LEFT JOIN inventory i ON i.variant_id = pv.id
                LEFT JOIN product_images pi
                  ON pi.product_id = p.id
                 AND pi.is_primary = 1
                LEFT JOIN files image_file ON image_file.id = pi.file_id
                WHERE p.is_active = 1
                  AND p.shop_id = :shopId
                  AND (:categoryId IS NULL OR p.shop_category_id = :categoryId)
                  AND (
                        :search IS NULL
                        OR p.name LIKE :search
                        OR sc.name LIKE :search
                        OR p.brand_name LIKE :search
                  )
                ORDER BY
                  out_of_stock ASC,
                  promotion_score DESC,
                  p.avg_rating DESC,
                  p.total_orders DESC,
                  p.updated_at DESC
                LIMIT :limit OFFSET :offset
                """;

        List<ShopProductCardResponse> rows = jdbcTemplate.query(sql, params, productCardMapper());
        boolean hasMore = rows.size() > safeSize;
        List<ShopProductCardResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    private static RowMapper<ShopSummaryResponse> shopSummaryMapper() {
        return (rs, rowNum) -> new ShopSummaryResponse(
                rs.getLong("shop_id"),
                nullableLong(rs, "shop_type_id"),
                rs.getString("shop_name"),
                rs.getString("shop_code"),
                valueOrZero(rs.getBigDecimal("avg_rating")),
                rs.getLong("total_reviews"),
                rs.getString("city"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                rs.getString("delivery_type"),
                rs.getBigDecimal("radius_km"),
                rs.getBigDecimal("min_order_amount"),
                rs.getBigDecimal("delivery_fee"),
                rs.getBoolean("open_now"),
                rs.getBoolean("closing_soon"),
                rs.getBoolean("accepts_orders"),
                rs.getString("closes_at")
        );
    }

    private static RowMapper<ShopProductCardResponse> productCardMapper() {
        return (rs, rowNum) -> new ShopProductCardResponse(
                rs.getLong("product_id"),
                nullableLong(rs, "variant_id"),
                rs.getLong("shop_id"),
                nullableLong(rs, "shop_type_id"),
                rs.getLong("category_id"),
                rs.getString("product_name"),
                rs.getString("shop_name"),
                rs.getString("category_name"),
                rs.getString("brand_name"),
                rs.getString("short_description"),
                rs.getString("product_type"),
                rs.getBigDecimal("mrp"),
                rs.getBigDecimal("selling_price"),
                valueOrZero(rs.getBigDecimal("avg_rating")),
                rs.getLong("total_reviews"),
                rs.getLong("total_orders"),
                rs.getString("inventory_status"),
                rs.getBoolean("out_of_stock"),
                rs.getInt("promotion_score"),
                rs.getString("image_object_key")
        );
    }

    private static Long nullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
