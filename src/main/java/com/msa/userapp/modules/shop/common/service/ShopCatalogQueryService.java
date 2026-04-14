package com.msa.userapp.modules.shop.common.service;

import com.msa.userapp.modules.shop.common.dto.HomeBootstrapResponse;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.modules.shop.common.dto.ShopCategoryResponse;
import com.msa.userapp.modules.shop.common.dto.ShopProductCardResponse;
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
public class ShopCatalogQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ShopCatalogQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HomeBootstrapResponse homeBootstrap(Double latitude, Double longitude, int page, int size) {
        return new HomeBootstrapResponse(
                findShopTypes(),
                findProducts(null, null, null, latitude, longitude, page, size)
        );
    }

    public List<ShopTypeResponse> findShopTypes() {
        String sql = """
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
                ORDER BY st.sort_order ASC, st.name ASC
                """;
        return jdbcTemplate.query(sql, Map.of(), shopTypeMapper());
    }

    public List<ShopCategoryResponse> findCategories(Long shopTypeId, Long parentCategoryId) {
        Map<String, Object> params = new HashMap<>();
        params.put("shopTypeId", shopTypeId);
        params.put("parentCategoryId", parentCategoryId);

        String sql = """
                SELECT
                    sc.id,
                    sc.parent_category_id,
                    stcm.shop_type_id,
                    COALESCE(sc.display_label, sc.name) AS name,
                    sc.normalized_name,
                    sc.theme_color,
                    stcm.is_coming_soon,
                    stcm.coming_soon_message,
                    image_file.object_key AS image_object_key,
                    COALESCE(NULLIF(stcm.sort_order, 0), sc.sort_order) AS sort_order
                FROM shop_type_category_mappings stcm
                INNER JOIN shop_categories sc ON sc.id = stcm.shop_category_id
                LEFT JOIN files image_file ON image_file.id = sc.image_file_id
                WHERE stcm.is_active = 1
                  AND sc.is_active = 1
                  AND (:shopTypeId IS NULL OR stcm.shop_type_id = :shopTypeId)
                  AND (
                        (:parentCategoryId IS NULL AND sc.parent_category_id IS NULL)
                        OR sc.parent_category_id = :parentCategoryId
                  )
                ORDER BY COALESCE(NULLIF(stcm.sort_order, 0), sc.sort_order), sc.name
                """;
        return jdbcTemplate.query(sql, params, categoryMapper());
    }

    public PageResponse<ShopProductCardResponse> findProducts(
            Long shopTypeId,
            Long categoryId,
            String search,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("shopTypeId", shopTypeId);
        params.put("categoryId", categoryId);
        params.put("search", hasText(search) ? "%" + search.trim() + "%" : null);
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
                  AND s.approval_status = 'APPROVED'
                  AND s.operational_status <> 'INACTIVE'
                  AND (:shopTypeId IS NULL OR s.shop_type_id = :shopTypeId)
                  AND (:categoryId IS NULL OR p.shop_category_id = :categoryId)
                  AND (
                        :search IS NULL
                        OR p.name LIKE :search
                        OR s.shop_name LIKE :search
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

        List<ShopProductCardResponse> rows = jdbcTemplate.query(sql, params, productMapper());
        boolean hasMore = rows.size() > safeSize;
        List<ShopProductCardResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private static RowMapper<ShopTypeResponse> shopTypeMapper() {
        return (rs, rowNum) -> new ShopTypeResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("theme_color"),
                rs.getBoolean("is_coming_soon"),
                rs.getString("coming_soon_message"),
                rs.getString("icon_object_key"),
                rs.getString("banner_object_key"),
                rs.getInt("sort_order")
        );
    }

    private static RowMapper<ShopCategoryResponse> categoryMapper() {
        return (rs, rowNum) -> new ShopCategoryResponse(
                rs.getLong("id"),
                nullableLong(rs, "parent_category_id"),
                rs.getLong("shop_type_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("theme_color"),
                rs.getBoolean("is_coming_soon"),
                rs.getString("coming_soon_message"),
                rs.getString("image_object_key"),
                rs.getInt("sort_order")
        );
    }

    private static RowMapper<ShopProductCardResponse> productMapper() {
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
