package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserEntity;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LabourDiscoveryRepository extends Repository<UserEntity, Long> {
    interface LabourCategoryRowView {
        Long getId();
        String getName();
        String getNormalizedName();
    }

    interface LabourProfileRowView {
        Long getLabourId();
        Long getCategoryId();
        String getCategoryName();
        String getFullName();
        String getPhotoObjectKey();
        String getPhone();
        Integer getExperienceYears();
        BigDecimal getHourlyRate();
        BigDecimal getHalfDayRate();
        BigDecimal getFullDayRate();
        BigDecimal getAvgRating();
        Long getTotalCompletedJobs();
        BigDecimal getDistanceKm();
        BigDecimal getRadiusKm();
        BigDecimal getWorkLatitude();
        BigDecimal getWorkLongitude();
        Boolean getOnlineStatus();
        Integer getAvailableNow();
        String getAvailabilityStatus();
        Integer getActiveBookingCount();
        String getSkillsSummary();
    }

    interface LabourCategoryPricingRowView {
        Long getCategoryId();
        String getCategoryName();
        BigDecimal getHalfDayRate();
        BigDecimal getFullDayRate();
    }

    @Query(value = """
            SELECT id, name, normalized_name AS normalizedName
            FROM labour_categories
            WHERE is_active = 1
            ORDER BY name ASC
            """, nativeQuery = true)
    List<LabourCategoryRowView> findActiveCategories();

    @Query(value = """
            WITH labour_rows AS (
                SELECT
                    lp.id AS labourId,
                    COALESCE(
                        MAX(CASE WHEN lc.id = :categoryId AND lpr.id IS NOT NULL THEN lc.id END),
                        MIN(CASE WHEN lpr.id IS NOT NULL THEN lc.id END)
                    ) AS categoryId,
                    COALESCE(
                        GROUP_CONCAT(DISTINCT CASE WHEN lpr.id IS NOT NULL THEN lc.name END ORDER BY lc.name SEPARATOR ', '),
                        'All labour'
                    ) AS categoryName,
                    COALESCE(up.full_name, CONCAT('Labour ', lp.id)) AS fullName,
                    COALESCE(photo.object_key, '') AS photoObjectKey,
                    u.phone AS phone,
                    lp.experience_years AS experienceYears,
                    lp.avg_rating AS avgRating,
                    GREATEST(lp.total_jobs_completed, COALESCE(MAX(completed_bookings.completed_job_count), 0)) AS totalCompletedJobs,
                    lp.online_status AS onlineStatus,
                    COALESCE(active_bookings.active_booking_count, 0) AS activeBookingCount,
                    COALESCE(MIN(CASE WHEN lpr.pricing_model = 'HOURLY' THEN lpr.hourly_price END), 0.00) AS hourlyRate,
                    COALESCE(MIN(CASE WHEN lpr.pricing_model = 'HALF_DAY' THEN lpr.half_day_price END), 0.00) AS halfDayRate,
                    COALESCE(MIN(CASE WHEN lpr.pricing_model = 'FULL_DAY' THEN lpr.full_day_price END), 0.00) AS fullDayRate,
                    COUNT(DISTINCT CASE WHEN lpr.id IS NOT NULL THEN lc.id END) AS enabledCategoryCount,
                    MAX(COALESCE(lsa.radius_km, 0)) AS radiusKm,
                    MIN(lsa.center_latitude) AS workLatitude,
                    MIN(lsa.center_longitude) AS workLongitude,
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
                    ) AS distanceKm,
                    CASE
                        WHEN COALESCE(active_bookings.active_booking_count, 0) > 0
                            OR COALESCE(active_requests.accepted_request_count, 0) > 0 THEN 'BOOKED'
                        WHEN lp.online_status = 1 THEN 'ONLINE'
                        ELSE 'OFFLINE'
                    END AS availabilityStatus,
                    CASE
                        WHEN lp.online_status = 1
                            AND COALESCE(active_bookings.active_booking_count, 0) = 0
                            AND COALESCE(active_requests.accepted_request_count, 0) = 0 THEN 1
                        ELSE 0
                    END AS availableNow,
                    CASE
                        WHEN lp.online_status = 1
                            AND COALESCE(active_bookings.active_booking_count, 0) = 0
                            AND COALESCE(active_requests.accepted_request_count, 0) = 0 THEN 0
                        WHEN COALESCE(active_bookings.active_booking_count, 0) > 0
                            OR COALESCE(active_requests.accepted_request_count, 0) > 0 THEN 1
                        ELSE 2
                    END AS availabilityRank,
                    GROUP_CONCAT(DISTINCT CASE WHEN lpr.id IS NOT NULL THEN ls.skill_name END ORDER BY ls.skill_name SEPARATOR ', ') AS skillsSummary
                FROM labour_profiles lp
                INNER JOIN users u ON u.id = lp.user_id
                LEFT JOIN user_profiles up ON up.user_id = u.id
                LEFT JOIN files photo ON photo.id = up.photo_file_id
                LEFT JOIN labour_skills ls ON ls.labour_id = lp.id
                LEFT JOIN labour_categories lc ON lc.id = ls.category_id
                LEFT JOIN labour_pricing lpr
                       ON lpr.labour_id = lp.id
                      AND lpr.category_id = ls.category_id
                      AND lpr.is_enabled = 1
                LEFT JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS active_booking_count
                    FROM bookings
                    WHERE provider_entity_type = 'LABOUR'
                      AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                    GROUP BY provider_entity_id
                ) active_bookings ON active_bookings.provider_entity_id = lp.id
                LEFT JOIN (
                    SELECT provider_entity_id, COUNT(1) AS completed_job_count
                    FROM bookings
                    WHERE provider_entity_type = 'LABOUR'
                      AND booking_status = 'COMPLETED'
                    GROUP BY provider_entity_id
                ) completed_bookings ON completed_bookings.provider_entity_id = lp.id
                LEFT JOIN (
                    SELECT brc.provider_entity_id, COUNT(1) AS accepted_request_count
                    FROM booking_request_candidates brc
                    INNER JOIN booking_requests br ON br.id = brc.request_id
                    WHERE brc.provider_entity_type = 'LABOUR'
                      AND brc.candidate_status = 'ACCEPTED'
                      AND br.request_status IN ('OPEN', 'ACCEPTED')
                      AND (br.request_status <> 'OPEN' OR br.expires_at > CURRENT_TIMESTAMP)
                    GROUP BY brc.provider_entity_id
                ) active_requests ON active_requests.provider_entity_id = lp.id
                WHERE lp.approval_status = 'APPROVED'
                  AND (:currentUserId IS NULL OR u.id <> :currentUserId)
                  AND (:currentUserPhone IS NULL OR u.phone <> :currentUserPhone)
                  AND (:categoryId IS NULL OR EXISTS (
                        SELECT 1
                        FROM labour_skills skill_filter
                        WHERE skill_filter.labour_id = lp.id
                          AND skill_filter.category_id = :categoryId
                          AND EXISTS (
                              SELECT 1
                              FROM labour_pricing price_filter
                              WHERE price_filter.labour_id = lp.id
                                AND price_filter.category_id = :categoryId
                                AND price_filter.is_enabled = 1
                          )
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
                    lp.total_jobs_completed,
                    lp.online_status,
                    active_bookings.active_booking_count,
                    active_requests.accepted_request_count
                HAVING (
                    :userLatitude IS NULL
                    OR :userLongitude IS NULL
                    OR distanceKm IS NULL
                    OR radiusKm <= 0
                    OR distanceKm <= radiusKm
                )
                AND enabledCategoryCount > 0
            )
            SELECT *
            FROM labour_rows
            ORDER BY
                availabilityRank ASC,
                distanceKm ASC,
                avgRating DESC,
                totalCompletedJobs DESC,
                labourId DESC
            """, nativeQuery = true)
    List<LabourProfileRowView> findProfiles(
            @Param("currentUserId") Long currentUserId,
            @Param("currentUserPhone") String currentUserPhone,
            @Param("categoryId") Long categoryId,
            @Param("search") String search,
            @Param("userCity") String userCity,
            @Param("userLatitude") BigDecimal userLatitude,
            @Param("userLongitude") BigDecimal userLongitude,
            Pageable pageable
    );

    @Query(value = """
            SELECT DISTINCT ls.skill_name
            FROM labour_skills ls
            INNER JOIN labour_pricing lpr
                ON lpr.labour_id = ls.labour_id
               AND lpr.category_id = ls.category_id
               AND lpr.is_enabled = 1
            WHERE ls.labour_id = :labourId
            ORDER BY ls.skill_name ASC
            """, nativeQuery = true)
    List<String> findEnabledSkillsByLabourId(@Param("labourId") Long labourId);

    @Query(value = """
            SELECT
                lc.id AS categoryId,
                lc.name AS categoryName,
                COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' THEN lpr.half_day_price END), 0.00) AS halfDayRate,
                COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' THEN lpr.full_day_price END), 0.00) AS fullDayRate
            FROM labour_pricing lpr
            INNER JOIN labour_categories lc ON lc.id = lpr.category_id
            WHERE lpr.labour_id = :labourId
              AND lpr.is_enabled = 1
            GROUP BY lc.id, lc.name
            ORDER BY lc.name ASC
            """, nativeQuery = true)
    List<LabourCategoryPricingRowView> findCategoryPricingsByLabourId(@Param("labourId") Long labourId);
}
