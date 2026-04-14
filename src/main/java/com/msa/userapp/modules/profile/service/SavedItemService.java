package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.SaveItemRequest;
import com.msa.userapp.modules.profile.dto.SavedItemResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedItemService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SavedItemService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<SavedItemResponse> list(Long userId, String targetType, String savedKind, int page, int size) {
        validateUserExists(userId);
        String normalizedTargetType = normalizeNullableEnum(targetType);
        String normalizedSavedKind = normalizeNullableEnum(savedKind);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        return jdbcTemplate.query("""
                SELECT
                    usi.id,
                    usi.target_type,
                    usi.target_id,
                    usi.saved_kind,
                    CASE usi.target_type
                        WHEN 'PRODUCT' THEN p.name
                        WHEN 'SHOP' THEN s.shop_name
                        WHEN 'LABOUR' THEN up.full_name
                        WHEN 'SERVICE_PROVIDER' THEN sup.full_name
                    END AS title,
                    CASE usi.target_type
                        WHEN 'PRODUCT' THEN COALESCE(sc.name, s.shop_name)
                        WHEN 'SHOP' THEN COALESCE(st.name, 'Shop')
                        WHEN 'LABOUR' THEN CONCAT('Jobs ', lp.total_jobs_completed)
                        WHEN 'SERVICE_PROVIDER' THEN CONCAT('Jobs ', sp.total_completed_jobs)
                    END AS subtitle,
                    CASE usi.target_type
                        WHEN 'PRODUCT' THEN product_file.object_key
                        WHEN 'SHOP' THEN shop_banner_file.object_key
                        WHEN 'LABOUR' THEN labour_file.object_key
                        WHEN 'SERVICE_PROVIDER' THEN provider_file.object_key
                    END AS image_object_key,
                    CASE usi.target_type
                        WHEN 'PRODUCT' THEN pv.selling_price
                        ELSE NULL
                    END AS price,
                    CASE usi.target_type
                        WHEN 'PRODUCT' THEN p.avg_rating
                        WHEN 'SHOP' THEN s.avg_rating
                        WHEN 'LABOUR' THEN lp.avg_rating
                        WHEN 'SERVICE_PROVIDER' THEN sp.avg_rating
                    END AS rating,
                    usi.created_at
                FROM user_saved_items usi
                LEFT JOIN products p
                  ON usi.target_type = 'PRODUCT'
                 AND p.id = usi.target_id
                LEFT JOIN shops s
                  ON (
                        (usi.target_type = 'PRODUCT' AND s.id = p.shop_id)
                        OR (usi.target_type = 'SHOP' AND s.id = usi.target_id)
                     )
                LEFT JOIN shop_categories sc
                  ON usi.target_type = 'PRODUCT'
                 AND sc.id = p.shop_category_id
                LEFT JOIN shop_types st
                  ON usi.target_type = 'SHOP'
                 AND st.id = s.shop_type_id
                LEFT JOIN product_variants pv
                  ON usi.target_type = 'PRODUCT'
                 AND pv.product_id = p.id
                 AND pv.is_default = 1
                 AND pv.is_active = 1
                LEFT JOIN product_images pi
                  ON usi.target_type = 'PRODUCT'
                 AND pi.product_id = p.id
                 AND pi.is_primary = 1
                LEFT JOIN files product_file
                  ON usi.target_type = 'PRODUCT'
                 AND product_file.id = pi.file_id
                LEFT JOIN files shop_banner_file
                  ON usi.target_type = 'SHOP'
                 AND shop_banner_file.id = st.banner_file_id
                LEFT JOIN labour_profiles lp
                  ON usi.target_type = 'LABOUR'
                 AND lp.id = usi.target_id
                LEFT JOIN user_profiles up
                  ON usi.target_type = 'LABOUR'
                 AND up.user_id = lp.user_id
                LEFT JOIN files labour_file
                  ON usi.target_type = 'LABOUR'
                 AND labour_file.id = up.photo_file_id
                LEFT JOIN service_providers sp
                  ON usi.target_type = 'SERVICE_PROVIDER'
                 AND sp.id = usi.target_id
                LEFT JOIN user_profiles sup
                  ON usi.target_type = 'SERVICE_PROVIDER'
                 AND sup.user_id = sp.user_id
                LEFT JOIN files provider_file
                  ON usi.target_type = 'SERVICE_PROVIDER'
                 AND provider_file.id = sup.photo_file_id
                WHERE usi.user_id = :userId
                  AND (:targetType IS NULL OR usi.target_type = :targetType)
                  AND (:savedKind IS NULL OR usi.saved_kind = :savedKind)
                ORDER BY usi.created_at DESC, usi.id DESC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetType", normalizedTargetType)
                .addValue("savedKind", normalizedSavedKind)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize), (rs, rowNum) -> new SavedItemResponse(
                rs.getLong("id"),
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getString("saved_kind"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("image_object_key"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("rating"),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    @Transactional
    public SavedItemResponse save(Long userId, SaveItemRequest request) {
        validateUserExists(userId);
        String targetType = normalizeRequiredEnum(request.targetType(), "targetType");
        String savedKind = normalizeRequiredEnum(request.savedKind(), "savedKind");
        validateTargetExists(targetType, request.targetId());

        jdbcTemplate.update("""
                INSERT INTO user_saved_items (user_id, target_type, target_id, saved_kind)
                VALUES (:userId, :targetType, :targetId, :savedKind)
                ON DUPLICATE KEY UPDATE created_at = created_at
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetType", targetType)
                .addValue("targetId", request.targetId())
                .addValue("savedKind", savedKind));

        return jdbcTemplate.query("""
                SELECT id
                FROM user_saved_items
                WHERE user_id = :userId
                  AND target_type = :targetType
                  AND target_id = :targetId
                  AND saved_kind = :savedKind
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetType", targetType)
                .addValue("targetId", request.targetId())
                .addValue("savedKind", savedKind), rs -> {
            if (!rs.next()) {
                throw new NotFoundException("Saved item not found after insert");
            }
            return list(userId, targetType, savedKind, 0, 1).stream()
                    .filter(item -> item.targetId().equals(request.targetId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Saved item not found after insert"));
        });
    }

    @Transactional
    public void remove(Long userId, String targetType, Long targetId, String savedKind) {
        validateUserExists(userId);
        String normalizedTargetType = normalizeRequiredEnum(targetType, "targetType");
        String normalizedSavedKind = normalizeRequiredEnum(savedKind, "savedKind");
        int updated = jdbcTemplate.update("""
                DELETE FROM user_saved_items
                WHERE user_id = :userId
                  AND target_type = :targetType
                  AND target_id = :targetId
                  AND saved_kind = :savedKind
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("targetType", normalizedTargetType)
                .addValue("targetId", targetId)
                .addValue("savedKind", normalizedSavedKind));
        if (updated == 0) {
            throw new NotFoundException("Saved item not found");
        }
    }

    private void validateTargetExists(String targetType, Long targetId) {
        String sql = switch (targetType) {
            case "PRODUCT" -> "SELECT COUNT(1) FROM products WHERE id = :targetId AND is_active = 1";
            case "SHOP" -> "SELECT COUNT(1) FROM shops WHERE id = :targetId AND approval_status = 'APPROVED'";
            case "LABOUR" -> "SELECT COUNT(1) FROM labour_profiles WHERE id = :targetId AND approval_status = 'APPROVED'";
            case "SERVICE_PROVIDER" -> "SELECT COUNT(1) FROM service_providers WHERE id = :targetId AND approval_status = 'APPROVED'";
            default -> throw new BadRequestException("Unsupported targetType");
        };
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("targetId", targetId), Integer.class);
        if (count == null || count == 0) {
            throw new NotFoundException("Target not found");
        }
    }

    private void validateUserExists(Long userId) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE id = :userId",
                Map.of("userId", userId),
                Integer.class
        );
        if (exists == null || exists == 0) {
            throw new NotFoundException("User not found");
        }
    }

    private static String normalizeRequiredEnum(String value, String fieldName) {
        String normalized = normalizeNullableEnum(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private static String normalizeNullableEnum(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
