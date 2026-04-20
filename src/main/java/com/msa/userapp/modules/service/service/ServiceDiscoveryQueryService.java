package com.msa.userapp.modules.service.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ServiceDiscoveryQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ServiceDiscoveryQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ServiceApiDtos.ServiceLandingResponse landing(
            Long userId,
            Long categoryId,
            Long subcategoryId,
            String city,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        return new ServiceApiDtos.ServiceLandingResponse(
                categories(),
                providers(userId, categoryId, subcategoryId, null, city, latitude, longitude, page, size)
        );
    }

    public List<ServiceApiDtos.ServiceCategoryResponse> categories() {
        Map<Long, ServiceApiDtos.ServiceCategoryResponse> categories = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT
                    pc.id AS category_id,
                    pc.name AS category_name,
                    psc.id AS subcategory_id,
                    psc.name AS subcategory_name
                FROM provider_categories pc
                LEFT JOIN provider_subcategories psc
                  ON psc.category_id = pc.id
                 AND psc.is_active = 1
                WHERE pc.is_active = 1
                ORDER BY pc.sort_order ASC, pc.name ASC, psc.name ASC
                """, rs -> {
            Long categoryId = rs.getLong("category_id");
            ServiceApiDtos.ServiceCategoryResponse existing = categories.get(categoryId);
            if (existing == null) {
                existing = new ServiceApiDtos.ServiceCategoryResponse(
                        categoryId,
                        rs.getString("category_name"),
                        new ArrayList<>()
                );
                categories.put(categoryId, existing);
            }
            Long subcategoryId = rs.getObject("subcategory_id") == null ? null : rs.getLong("subcategory_id");
            if (subcategoryId != null) {
                @SuppressWarnings("unchecked")
                List<ServiceApiDtos.ServiceSubcategoryResponse> subcategories =
                        (List<ServiceApiDtos.ServiceSubcategoryResponse>) existing.subcategories();
                subcategories.add(new ServiceApiDtos.ServiceSubcategoryResponse(
                        subcategoryId,
                        categoryId,
                        rs.getString("subcategory_name")
                ));
            }
        });
        return new ArrayList<>(categories.values());
    }

    public PageResponse<ServiceApiDtos.ServiceProviderCardResponse> providers(
            Long userId,
            Long categoryId,
            Long subcategoryId,
            String search,
            int page,
            int size
    ) {
        return providers(userId, categoryId, subcategoryId, search, null, null, null, page, size);
    }

    public PageResponse<ServiceApiDtos.ServiceProviderCardResponse> providers(
            Long userId,
            Long categoryId,
            Long subcategoryId,
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
        String currentUserPhone = resolveUserPhone(userId);

        Map<String, Object> params = new HashMap<>();
        params.put("currentUserId", userId);
        params.put("currentUserPhone", currentUserPhone);
        params.put("categoryId", categoryId);
        params.put("subcategoryId", subcategoryId);
        params.put("search", StringUtils.hasText(search) ? "%" + search.trim() + "%" : null);
        params.put("userCity", userLocation.city());
        params.put("userLatitude", userLocation.latitude());
        params.put("userLongitude", userLocation.longitude());
        params.put("limit", limit);
        params.put("offset", offset);

        String sql = """
                WITH provider_rows AS (
                    SELECT
                        sp.id AS provider_id,
                        COALESCE(MAX(CASE WHEN pc.id = :categoryId THEN pc.id END), MIN(pc.id)) AS category_id,
                        COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN psc.id END), MIN(psc.id)) AS subcategory_id,
                        COALESCE(MAX(CASE WHEN pc.id = :categoryId THEN pc.name END), MIN(pc.name), 'Service') AS category_name,
                        COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN psc.name END), MIN(psc.name), 'All') AS subcategory_name,
                        COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS provider_name,
                        COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN ps.service_name END), MIN(ps.service_name), 'Service') AS service_name,
                        COALESCE(photo.object_key, '') AS photo_object_key,
                        u.phone,
                        COALESCE(MAX(ppr.visiting_charge), 0.00) AS visiting_charge,
                        sp.avg_rating,
                        sp.total_completed_jobs,
                        sp.available_service_men,
                        sp.online_status,
                        COALESCE(active_bookings.active_booking_count, 0) AS active_booking_count,
                        GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) AS remaining_service_men,
                        MIN(
                            CASE
                                WHEN :userLatitude IS NOT NULL AND :userLongitude IS NOT NULL THEN
                                    6371 * ACOS(
                                        LEAST(
                                            1,
                                            COS(RADIANS(:userLatitude)) * COS(RADIANS(psa.center_latitude))
                                            * COS(RADIANS(psa.center_longitude) - RADIANS(:userLongitude))
                                            + SIN(RADIANS(:userLatitude)) * SIN(RADIANS(psa.center_latitude))
                                        )
                                    )
                                ELSE NULL
                            END
                        ) AS distance_km,
                        CASE
                            WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 'ONLINE'
                            WHEN GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) <= 0 THEN 'BOOKED'
                            ELSE 'OFFLINE'
                        END AS availability_status,
                        CASE
                            WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 1
                            ELSE 0
                        END AS available_now,
                        CASE
                            WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 0
                            WHEN GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) <= 0 THEN 1
                            ELSE 2
                        END AS availability_rank
                    FROM service_providers sp
                    INNER JOIN users u ON u.id = sp.user_id
                    LEFT JOIN user_profiles up ON up.user_id = u.id
                    LEFT JOIN files photo ON photo.id = up.photo_file_id
                    LEFT JOIN provider_services ps
                        ON ps.provider_id = sp.id
                       AND ps.is_active = 1
                    LEFT JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
                    LEFT JOIN provider_categories pc ON pc.id = psc.category_id
                    LEFT JOIN provider_pricing_rules ppr ON ppr.provider_service_id = ps.id
                    LEFT JOIN provider_service_areas psa ON psa.provider_id = sp.id
                    LEFT JOIN (
                        SELECT provider_entity_id, COUNT(1) AS active_booking_count
                        FROM bookings
                        WHERE provider_entity_type = 'SERVICE_PROVIDER'
                          AND booking_status IN ('ACCEPTED', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                        GROUP BY provider_entity_id
                    ) active_bookings ON active_bookings.provider_entity_id = sp.id
                    WHERE sp.approval_status = 'APPROVED'
                      AND (:currentUserId IS NULL OR u.id <> :currentUserId)
                      AND (:currentUserPhone IS NULL OR u.phone <> :currentUserPhone)
                      AND (:categoryId IS NULL OR EXISTS (
                            SELECT 1
                            FROM provider_services ps_filter
                            INNER JOIN provider_subcategories psc_filter ON psc_filter.id = ps_filter.subcategory_id
                            WHERE ps_filter.provider_id = sp.id
                              AND ps_filter.is_active = 1
                              AND psc_filter.category_id = :categoryId
                      ))
                      AND (:subcategoryId IS NULL OR EXISTS (
                            SELECT 1
                            FROM provider_services ps_filter
                            WHERE ps_filter.provider_id = sp.id
                              AND ps_filter.is_active = 1
                              AND ps_filter.subcategory_id = :subcategoryId
                      ))
                      AND (:userCity IS NULL OR EXISTS (
                            SELECT 1
                            FROM provider_service_areas city_filter
                            WHERE city_filter.provider_id = sp.id
                              AND city_filter.city = :userCity
                      ))
                      AND (
                            :search IS NULL
                            OR up.full_name LIKE :search
                            OR ps.service_name LIKE :search
                            OR psc.name LIKE :search
                            OR pc.name LIKE :search
                      )
                    GROUP BY
                        sp.id,
                        up.full_name,
                        photo.object_key,
                        u.phone,
                        sp.avg_rating,
                        sp.total_completed_jobs,
                        sp.available_service_men,
                        sp.online_status,
                        active_bookings.active_booking_count
                ),
                preferred_rank AS (
                    SELECT MIN(availability_rank) AS selected_rank
                    FROM provider_rows
                )
                SELECT *
                FROM provider_rows
                WHERE availability_rank = (SELECT selected_rank FROM preferred_rank)
                ORDER BY
                    distance_km ASC,
                    avg_rating DESC,
                    total_completed_jobs DESC,
                    provider_id DESC
                LIMIT :limit OFFSET :offset
                """;

        List<ServiceApiDtos.ServiceProviderCardResponse> rows = jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new ServiceApiDtos.ServiceProviderCardResponse(
                        rs.getLong("provider_id"),
                        rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                        rs.getObject("subcategory_id") == null ? null : rs.getLong("subcategory_id"),
                        rs.getString("category_name"),
                        rs.getString("subcategory_name"),
                        rs.getString("provider_name"),
                        rs.getString("service_name"),
                        rs.getString("photo_object_key"),
                        maskPhone(rs.getString("phone")),
                        rs.getBigDecimal("visiting_charge"),
                        rs.getBigDecimal("avg_rating"),
                        rs.getLong("total_completed_jobs"),
                        rs.getInt("available_service_men"),
                        rs.getBigDecimal("distance_km"),
                        rs.getBoolean("online_status"),
                        rs.getBoolean("available_now"),
                        rs.getString("availability_status"),
                        rs.getInt("active_booking_count"),
                        rs.getInt("remaining_service_men")
                ));
        boolean hasMore = rows.size() > safeSize;
        List<ServiceApiDtos.ServiceProviderCardResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    public ServiceApiDtos.ServiceProviderProfileResponse providerProfile(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId
    ) {
        ServiceApiDtos.ServiceProviderCardResponse provider = requireProvider(userId, providerId, categoryId, subcategoryId);
        List<String> serviceItems = jdbcTemplate.query("""
                SELECT DISTINCT psi.item_name
                FROM provider_service_items psi
                INNER JOIN provider_services ps ON ps.id = psi.provider_service_id
                WHERE ps.provider_id = :providerId
                  AND psi.is_active = 1
                ORDER BY psi.item_name ASC
                """, Map.of("providerId", providerId), (rs, rowNum) -> rs.getString("item_name"));
        return new ServiceApiDtos.ServiceProviderProfileResponse(provider, serviceItems);
    }

    public Long resolveDefaultAddressId(Long userId, Long explicitAddressId) {
        if (explicitAddressId != null) {
            List<Long> rows = jdbcTemplate.query("""
                    SELECT id
                    FROM user_addresses
                    WHERE id = :addressId
                      AND user_id = :userId
                      AND address_scope = 'CONSUMER'
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
                  AND address_scope = 'CONSUMER'
                  AND is_booking_temp = 0
                ORDER BY is_default DESC, id ASC
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new NotFoundException("Please add an address before booking service");
        }
        return rows.getFirst();
    }

    ServiceApiDtos.ServiceProviderCardResponse requireProvider(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId
    ) {
        PageResponse<ServiceApiDtos.ServiceProviderCardResponse> page = providers(
                userId,
                categoryId,
                subcategoryId,
                null,
                null,
                null,
                null,
                0,
                MAX_PAGE_SIZE
        );
        return page.items().stream()
                .filter(item -> item.providerId().equals(providerId))
                .findFirst()
                .orElseGet(() -> loadSingleProvider(userId, providerId, categoryId, subcategoryId));
    }

    private ServiceApiDtos.ServiceProviderCardResponse loadSingleProvider(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId
    ) {
        UserLocation userLocation = resolveUserLocation(userId, null, null, null);
        String currentUserPhone = resolveUserPhone(userId);
        Map<String, Object> params = new HashMap<>();
        params.put("providerId", providerId);
        params.put("currentUserId", userId);
        params.put("currentUserPhone", currentUserPhone);
        params.put("categoryId", categoryId);
        params.put("subcategoryId", subcategoryId);
        params.put("userLatitude", userLocation.latitude());
        params.put("userLongitude", userLocation.longitude());
        List<ServiceApiDtos.ServiceProviderCardResponse> rows = jdbcTemplate.query("""
                SELECT
                    sp.id AS provider_id,
                    COALESCE(MAX(CASE WHEN pc.id = :categoryId THEN pc.id END), MIN(pc.id)) AS category_id,
                    COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN psc.id END), MIN(psc.id)) AS subcategory_id,
                    COALESCE(MAX(CASE WHEN pc.id = :categoryId THEN pc.name END), MIN(pc.name), 'Service') AS category_name,
                    COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN psc.name END), MIN(psc.name), 'All') AS subcategory_name,
                    COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS provider_name,
                    COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN ps.service_name END), MIN(ps.service_name), 'Service') AS service_name,
                    COALESCE(photo.object_key, '') AS photo_object_key,
                    u.phone,
                    COALESCE(MAX(ppr.visiting_charge), 0.00) AS visiting_charge,
                    sp.avg_rating,
                    sp.total_completed_jobs,
                    sp.available_service_men,
                    sp.online_status,
                    COALESCE(active_bookings.active_booking_count, 0) AS active_booking_count,
                    GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) AS remaining_service_men,
                    MIN(
                        CASE
                            WHEN :userLatitude IS NOT NULL AND :userLongitude IS NOT NULL THEN
                                6371 * ACOS(
                                    LEAST(
                                        1,
                                        COS(RADIANS(:userLatitude)) * COS(RADIANS(psa.center_latitude))
                                        * COS(RADIANS(psa.center_longitude) - RADIANS(:userLongitude))
                                        + SIN(RADIANS(:userLatitude)) * SIN(RADIANS(psa.center_latitude))
                                    )
                                )
                            ELSE NULL
                        END
                    ) AS distance_km
                    ,
                    CASE
                        WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 'ONLINE'
                        WHEN GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) <= 0 THEN 'BOOKED'
                        ELSE 'OFFLINE'
                    END AS availability_status,
                    CASE
                        WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 1
                        ELSE 0
                    END AS available_now
                FROM service_providers sp
                INNER JOIN users u ON u.id = sp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                LEFT JOIN files photo ON photo.id = up.photo_file_id
                LEFT JOIN provider_services ps
                    ON ps.provider_id = sp.id
                   AND ps.is_active = 1
                LEFT JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
                LEFT JOIN provider_categories pc ON pc.id = psc.category_id
                LEFT JOIN provider_pricing_rules ppr ON ppr.provider_service_id = ps.id
                LEFT JOIN provider_service_areas psa ON psa.provider_id = sp.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS active_booking_count
                    FROM bookings
                    WHERE provider_entity_type = 'SERVICE_PROVIDER'
                      AND booking_status IN ('ACCEPTED', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                    GROUP BY provider_entity_id
                ) active_bookings ON active_bookings.provider_entity_id = sp.id
                WHERE sp.id = :providerId
                  AND sp.approval_status = 'APPROVED'
                  AND (:currentUserId IS NULL OR u.id <> :currentUserId)
                  AND (:currentUserPhone IS NULL OR u.phone <> :currentUserPhone)
                GROUP BY
                    sp.id,
                    up.full_name,
                    photo.object_key,
                    u.phone,
                    sp.avg_rating,
                    sp.total_completed_jobs,
                    sp.available_service_men,
                    sp.online_status,
                    active_bookings.active_booking_count
                LIMIT 1
                """, params, (rs, rowNum) -> new ServiceApiDtos.ServiceProviderCardResponse(
                rs.getLong("provider_id"),
                rs.getObject("category_id") == null ? null : rs.getLong("category_id"),
                rs.getObject("subcategory_id") == null ? null : rs.getLong("subcategory_id"),
                rs.getString("category_name"),
                rs.getString("subcategory_name"),
                rs.getString("provider_name"),
                rs.getString("service_name"),
                rs.getString("photo_object_key"),
                maskPhone(rs.getString("phone")),
                rs.getBigDecimal("visiting_charge"),
                rs.getBigDecimal("avg_rating"),
                rs.getLong("total_completed_jobs"),
                rs.getInt("available_service_men"),
                rs.getBigDecimal("distance_km"),
                rs.getBoolean("online_status"),
                rs.getBoolean("available_now"),
                rs.getString("availability_status"),
                rs.getInt("active_booking_count"),
                rs.getInt("remaining_service_men")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Service provider not found");
        }
        return rows.getFirst();
    }

    private UserLocation resolveUserLocation(Long userId, String overrideCity, Double overrideLatitude, Double overrideLongitude) {
        if (overrideLatitude != null && overrideLongitude != null) {
            return new UserLocation(
                    StringUtils.hasText(overrideCity) ? overrideCity.trim() : null,
                    BigDecimal.valueOf(overrideLatitude),
                    BigDecimal.valueOf(overrideLongitude)
            );
        }
        if (userId == null || userId <= 0) {
            return new UserLocation(null, null, null);
        }
        List<UserLocation> rows = jdbcTemplate.query("""
                SELECT city, latitude, longitude
                FROM user_addresses
                WHERE user_id = :userId
                  AND address_scope = 'CONSUMER'
                ORDER BY is_default DESC, id ASC
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> new UserLocation(
                rs.getString("city"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude")
        ));
        return rows.isEmpty() ? new UserLocation(null, null, null) : rows.getFirst();
    }

    private String resolveUserPhone(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        List<String> rows = jdbcTemplate.query("""
                SELECT phone
                FROM users
                WHERE id = :userId
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> rs.getString("phone"));
        if (rows.isEmpty()) {
            return null;
        }
        String phone = rows.getFirst();
        return StringUtils.hasText(phone) ? phone.trim() : null;
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
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
