package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserEntity;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ServiceDiscoveryRepository extends Repository<UserEntity, Long> {
    interface ServiceCategoryRowView {
        Long getCategoryId();
        String getCategoryName();
        Long getSubcategoryId();
        String getSubcategoryName();
    }

    interface ServiceProviderRowView {
        Long getProviderId();
        Long getCategoryId();
        Long getSubcategoryId();
        String getCategoryName();
        String getSubcategoryName();
        String getProviderName();
        String getServiceName();
        String getPhotoObjectKey();
        String getServiceItems();
        String getPhone();
        BigDecimal getVisitingCharge();
        BigDecimal getAvgRating();
        Long getTotalCompletedJobs();
        Integer getAvailableServiceMen();
        BigDecimal getDistanceKm();
        Boolean getOnlineStatus();
        Boolean getAvailableNow();
        String getAvailabilityStatus();
        Integer getActiveBookingCount();
        Integer getRemainingServiceMen();
    }

    @Query(value = """
            SELECT
                pc.id AS categoryId,
                pc.name AS categoryName,
                psc.id AS subcategoryId,
                psc.name AS subcategoryName
            FROM provider_categories pc
            LEFT JOIN provider_subcategories psc
              ON psc.category_id = pc.id
             AND psc.is_active = 1
            WHERE pc.is_active = 1
            ORDER BY pc.sort_order ASC, pc.name ASC, psc.name ASC
            """, nativeQuery = true)
    List<ServiceCategoryRowView> findActiveCategories();

    @Query(value = """
            WITH provider_rows AS (
                SELECT
                    sp.id AS providerId,
                    COALESCE(MAX(CASE WHEN pc.id = :categoryId THEN pc.id END), MIN(pc.id)) AS categoryId,
                    COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN psc.id END), MIN(psc.id)) AS subcategoryId,
                    COALESCE(MAX(CASE WHEN pc.id = :categoryId THEN pc.name END), MIN(pc.name), 'Service') AS categoryName,
                    COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN psc.name END), MIN(psc.name), 'All') AS subcategoryName,
                    COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS providerName,
                    COALESCE(MAX(CASE WHEN psc.id = :subcategoryId THEN ps.service_name END), MIN(ps.service_name), 'Service') AS serviceName,
                    COALESCE(photo.object_key, '') AS photoObjectKey,
                    COALESCE(provider_items.service_items, '') AS serviceItems,
                    u.phone AS phone,
                    COALESCE(MAX(ppr.visiting_charge), 0.00) AS visitingCharge,
                    sp.avg_rating AS avgRating,
                    sp.total_completed_jobs AS totalCompletedJobs,
                    sp.available_service_men AS availableServiceMen,
                    sp.online_status AS onlineStatus,
                    COALESCE(active_bookings.active_booking_count, 0) AS activeBookingCount,
                    GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) AS remainingServiceMen,
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
                    ) AS distanceKm,
                    CASE
                        WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 'ONLINE'
                        WHEN GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) <= 0 THEN 'BOOKED'
                        ELSE 'OFFLINE'
                    END AS availabilityStatus,
                    CASE
                        WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 1
                        ELSE 0
                    END AS availableNow,
                    CASE
                        WHEN sp.online_status = 1 AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0 THEN 0
                        WHEN GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) <= 0 THEN 1
                        ELSE 2
                    END AS availabilityRank
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
                    SELECT
                        ps_items.provider_id,
                        GROUP_CONCAT(DISTINCT psi.item_name ORDER BY psi.item_name SEPARATOR '||') AS service_items
                    FROM provider_service_items psi
                    INNER JOIN provider_services ps_items ON ps_items.id = psi.provider_service_id
                    INNER JOIN provider_subcategories psc_items ON psc_items.id = ps_items.subcategory_id
                    WHERE psi.is_active = 1
                      AND ps_items.is_active = 1
                      AND (:categoryId IS NULL OR psc_items.category_id = :categoryId)
                      AND (:subcategoryId IS NULL OR ps_items.subcategory_id = :subcategoryId)
                    GROUP BY ps_items.provider_id
                ) provider_items ON provider_items.provider_id = sp.id
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
            )
            SELECT *
            FROM provider_rows
            ORDER BY
                availabilityRank ASC,
                CASE WHEN distanceKm IS NULL THEN 1 ELSE 0 END ASC,
                distanceKm ASC,
                avgRating DESC,
                totalCompletedJobs DESC,
                providerId DESC
            """, nativeQuery = true)
    List<ServiceProviderRowView> findProviders(
            @Param("currentUserId") Long currentUserId,
            @Param("currentUserPhone") String currentUserPhone,
            @Param("categoryId") Long categoryId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("search") String search,
            @Param("userCity") String userCity,
            @Param("userLatitude") BigDecimal userLatitude,
            @Param("userLongitude") BigDecimal userLongitude,
            Pageable pageable
    );

    @Query(value = """
            SELECT DISTINCT psi.item_name
            FROM provider_service_items psi
            INNER JOIN provider_services ps ON ps.id = psi.provider_service_id
            INNER JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
            WHERE ps.provider_id = :providerId
              AND psi.is_active = 1
              AND (:categoryId IS NULL OR psc.category_id = :categoryId)
              AND (:subcategoryId IS NULL OR ps.subcategory_id = :subcategoryId)
            ORDER BY psi.item_name ASC
            """, nativeQuery = true)
    List<String> findServiceItemsByProviderId(
            @Param("providerId") Long providerId,
            @Param("categoryId") Long categoryId,
            @Param("subcategoryId") Long subcategoryId
    );
}
