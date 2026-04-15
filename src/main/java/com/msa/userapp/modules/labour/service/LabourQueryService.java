package com.msa.userapp.modules.labour.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LabourQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LabourQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LabourApiDtos.LabourLandingResponse landing(
            Long userId,
            Long categoryId,
            String city,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        return new LabourApiDtos.LabourLandingResponse(
                categories(),
                profiles(userId, categoryId, null, city, latitude, longitude, page, size)
        );
    }

    public List<LabourApiDtos.LabourCategoryResponse> categories() {
        return jdbcTemplate.query("""
                SELECT id, name, normalized_name
                FROM labour_categories
                WHERE is_active = 1
                ORDER BY name ASC
                """, (rs, rowNum) -> new LabourApiDtos.LabourCategoryResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("normalized_name")
        ));
    }

    public PageResponse<LabourApiDtos.LabourProfileCardResponse> profiles(
            Long userId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        return profiles(userId, categoryId, search, null, null, null, page, size);
    }

    public PageResponse<LabourApiDtos.LabourProfileCardResponse> profiles(
            Long userId,
            Long categoryId,
            String search,
            String city,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;
        UserLocation userLocation = resolveUserLocation(userId, city, latitude, longitude);

        Map<String, Object> params = new HashMap<>();
        params.put("categoryId", categoryId);
        params.put("search", StringUtils.hasText(search) ? "%" + search.trim() + "%" : null);
        params.put("userCity", userLocation.city());
        params.put("userLatitude", userLocation.latitude());
        params.put("userLongitude", userLocation.longitude());
        params.put("limit", limit);
        params.put("offset", offset);

        String sql = """
                SELECT
                    lp.id AS labour_id,
                    COALESCE(MAX(CASE WHEN lc.id = :categoryId THEN lc.id END), MIN(lc.id)) AS category_id,
                    COALESCE(MAX(CASE WHEN lc.id = :categoryId THEN lc.name END), MIN(lc.name), 'All labour') AS category_name,
                    COALESCE(up.full_name, CONCAT('Labour ', lp.id)) AS full_name,
                    COALESCE(photo.object_key, '') AS photo_object_key,
                    u.phone,
                    lp.experience_years,
                    lp.avg_rating,
                    lp.total_jobs_completed,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HOURLY' AND lpr.is_enabled = 1 THEN lpr.hourly_price END), 0.00) AS hourly_rate,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' AND lpr.is_enabled = 1 THEN lpr.half_day_price END), 0.00) AS half_day_rate,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' AND lpr.is_enabled = 1 THEN lpr.full_day_price END), 0.00) AS full_day_rate,
                    MIN(
                        CASE
                            WHEN :userLatitude IS NOT NULL AND :userLongitude IS NOT NULL THEN
                                6371 * ACOS(
                                    LEAST(
                                        1,
                                        COS(RADIANS(:userLatitude)) * COS(RADIANS(lsa.center_latitude))
                                        * COS(RADIANS(lsa.center_longitude) - RADIANS(:userLongitude))
                                        + SIN(RADIANS(:userLatitude)) * SIN(RADIANS(lsa.center_latitude))
                                    )
                                )
                            ELSE NULL
                        END
                    ) AS distance_km,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM labour_availability la
                            WHERE la.labour_id = lp.id
                              AND la.available_date = CURRENT_DATE()
                              AND la.availability_status = 'AVAILABLE'
                        ) THEN 1
                        ELSE 0
                    END AS available_today,
                    GROUP_CONCAT(DISTINCT ls.skill_name ORDER BY ls.skill_name SEPARATOR ', ') AS skills_summary
                FROM labour_profiles lp
                INNER JOIN users u ON u.id = lp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                LEFT JOIN files photo ON photo.id = up.photo_file_id
                LEFT JOIN labour_skills ls ON ls.labour_id = lp.id
                LEFT JOIN labour_categories lc ON lc.id = ls.category_id
                LEFT JOIN labour_pricing lpr ON lpr.labour_id = lp.id
                LEFT JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
                WHERE lp.approval_status = 'APPROVED'
                  AND (:categoryId IS NULL OR EXISTS (
                        SELECT 1
                        FROM labour_skills skill_filter
                        WHERE skill_filter.labour_id = lp.id
                          AND skill_filter.category_id = :categoryId
                  ))
                  AND (:userCity IS NULL OR EXISTS (
                        SELECT 1
                        FROM labour_service_areas city_filter
                        WHERE city_filter.labour_id = lp.id
                          AND city_filter.city = :userCity
                  ))
                  AND (
                        :search IS NULL
                        OR up.full_name LIKE :search
                        OR ls.skill_name LIKE :search
                        OR lc.name LIKE :search
                  )
                GROUP BY
                    lp.id,
                    up.full_name,
                    photo.object_key,
                    u.phone,
                    lp.experience_years,
                    lp.avg_rating,
                    lp.total_jobs_completed
                ORDER BY
                    available_today DESC,
                    lp.avg_rating DESC,
                    lp.total_jobs_completed DESC,
                    distance_km ASC,
                    lp.id DESC
                LIMIT :limit OFFSET :offset
                """;

        List<LabourApiDtos.LabourProfileCardResponse> rows = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new LabourApiDtos.LabourProfileCardResponse(
                        rs.getLong("labour_id"),
                        rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                        rs.getString("category_name"),
                        rs.getString("full_name"),
                        rs.getString("photo_object_key"),
                        maskPhone(rs.getString("phone")),
                        rs.getInt("experience_years"),
                        rs.getBigDecimal("hourly_rate"),
                        rs.getBigDecimal("half_day_rate"),
                        rs.getBigDecimal("full_day_rate"),
                        rs.getBigDecimal("avg_rating"),
                        rs.getLong("total_jobs_completed"),
                        rs.getBigDecimal("distance_km"),
                        rs.getBoolean("available_today"),
                        rs.getString("skills_summary")
                ));
        boolean hasMore = rows.size() > safeSize;
        List<LabourApiDtos.LabourProfileCardResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    public LabourApiDtos.LabourProfileResponse profile(Long userId, Long labourId) {
        PageResponse<LabourApiDtos.LabourProfileCardResponse> page = profiles(
                userId,
                null,
                null,
                null,
                null,
                null,
                0,
                MAX_PAGE_SIZE
        );
        LabourApiDtos.LabourProfileCardResponse profile = page.items().stream()
                .filter(item -> item.labourId().equals(labourId))
                .findFirst()
                .orElseGet(() -> requireProfile(userId, labourId));

        List<String> skills = jdbcTemplate.query("""
                SELECT DISTINCT ls.skill_name
                FROM labour_skills ls
                WHERE ls.labour_id = :labourId
                ORDER BY ls.skill_name ASC
                """, Map.of("labourId", labourId), (rs, rowNum) -> rs.getString("skill_name"));

        return new LabourApiDtos.LabourProfileResponse(profile, skills);
    }

    public Long resolveDefaultAddressId(Long userId, Long explicitAddressId) {
        if (explicitAddressId != null) {
            List<Long> rows = jdbcTemplate.query("""
                    SELECT id
                    FROM user_addresses
                    WHERE id = :addressId
                      AND user_id = :userId
                    LIMIT 1
                    """, Map.of("addressId", explicitAddressId, "userId", userId), (rs, rowNum) -> rs.getLong("id"));
            if (rows.isEmpty()) {
                throw new NotFoundException("Address not found for this user");
            }
            return rows.getFirst();
        }
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM user_addresses
                WHERE user_id = :userId
                ORDER BY is_default DESC, id ASC
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new NotFoundException("Please add an address before booking labour");
        }
        return rows.getFirst();
    }

    private LabourApiDtos.LabourProfileCardResponse requireProfile(Long userId, Long labourId) {
        UserLocation userLocation = resolveUserLocation(userId, null, null, null);
        Map<String, Object> params = new HashMap<>();
        params.put("labourId", labourId);
        params.put("userCity", userLocation.city());
        params.put("userLatitude", userLocation.latitude());
        params.put("userLongitude", userLocation.longitude());
        List<LabourApiDtos.LabourProfileCardResponse> rows = jdbcTemplate.query("""
                SELECT
                    lp.id AS labour_id,
                    MIN(lc.id) AS category_id,
                    COALESCE(MIN(lc.name), 'All labour') AS category_name,
                    COALESCE(up.full_name, CONCAT('Labour ', lp.id)) AS full_name,
                    COALESCE(photo.object_key, '') AS photo_object_key,
                    u.phone,
                    lp.experience_years,
                    lp.avg_rating,
                    lp.total_jobs_completed,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HOURLY' AND lpr.is_enabled = 1 THEN lpr.hourly_price END), 0.00) AS hourly_rate,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' AND lpr.is_enabled = 1 THEN lpr.half_day_price END), 0.00) AS half_day_rate,
                    COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' AND lpr.is_enabled = 1 THEN lpr.full_day_price END), 0.00) AS full_day_rate,
                    MIN(
                        CASE
                            WHEN :userLatitude IS NOT NULL AND :userLongitude IS NOT NULL THEN
                                6371 * ACOS(
                                    LEAST(
                                        1,
                                        COS(RADIANS(:userLatitude)) * COS(RADIANS(lsa.center_latitude))
                                        * COS(RADIANS(lsa.center_longitude) - RADIANS(:userLongitude))
                                        + SIN(RADIANS(:userLatitude)) * SIN(RADIANS(lsa.center_latitude))
                                    )
                                )
                            ELSE NULL
                        END
                    ) AS distance_km,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM labour_availability la
                            WHERE la.labour_id = lp.id
                              AND la.available_date = CURRENT_DATE()
                              AND la.availability_status = 'AVAILABLE'
                        ) THEN 1
                        ELSE 0
                    END AS available_today,
                    GROUP_CONCAT(DISTINCT ls.skill_name ORDER BY ls.skill_name SEPARATOR ', ') AS skills_summary
                FROM labour_profiles lp
                INNER JOIN users u ON u.id = lp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                LEFT JOIN files photo ON photo.id = up.photo_file_id
                LEFT JOIN labour_skills ls ON ls.labour_id = lp.id
                LEFT JOIN labour_categories lc ON lc.id = ls.category_id
                LEFT JOIN labour_pricing lpr ON lpr.labour_id = lp.id
                LEFT JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
                WHERE lp.id = :labourId
                  AND lp.approval_status = 'APPROVED'
                GROUP BY
                    lp.id,
                    up.full_name,
                    photo.object_key,
                    u.phone,
                    lp.experience_years,
                    lp.avg_rating,
                    lp.total_jobs_completed
                LIMIT 1
                """, params, (rs, rowNum) -> new LabourApiDtos.LabourProfileCardResponse(
                rs.getLong("labour_id"),
                rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("full_name"),
                rs.getString("photo_object_key"),
                maskPhone(rs.getString("phone")),
                rs.getInt("experience_years"),
                rs.getBigDecimal("hourly_rate"),
                rs.getBigDecimal("half_day_rate"),
                rs.getBigDecimal("full_day_rate"),
                rs.getBigDecimal("avg_rating"),
                rs.getLong("total_jobs_completed"),
                rs.getBigDecimal("distance_km"),
                rs.getBoolean("available_today"),
                rs.getString("skills_summary")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Labour profile not found");
        }
        return rows.getFirst();
    }

    private UserLocation resolveUserLocation(Long userId, String overrideCity, Double overrideLatitude, Double overrideLongitude) {
        if (overrideLatitude != null && overrideLongitude != null) {
            return new UserLocation(
                    null,
                    StringUtils.hasText(overrideCity) ? overrideCity.trim() : null,
                    BigDecimal.valueOf(overrideLatitude),
                    BigDecimal.valueOf(overrideLongitude)
            );
        }
        if (userId == null || userId <= 0) {
            return new UserLocation(null, null, null, null);
        }
        List<UserLocation> rows = jdbcTemplate.query("""
                SELECT id, city, latitude, longitude
                FROM user_addresses
                WHERE user_id = :userId
                ORDER BY is_default DESC, id ASC
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> new UserLocation(
                rs.getLong("id"),
                rs.getString("city"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude")
        ));
        return rows.isEmpty() ? new UserLocation(null, null, null, null) : rows.getFirst();
    }

    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String normalized = phone.trim();
        if (normalized.length() <= 4) {
            return normalized;
        }
        String prefix = normalized.substring(0, Math.min(2, normalized.length()));
        String suffix = normalized.substring(Math.max(normalized.length() - 2, 0));
        return prefix + "xxxxxx" + suffix;
    }

    private record UserLocation(
            Long addressId,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
