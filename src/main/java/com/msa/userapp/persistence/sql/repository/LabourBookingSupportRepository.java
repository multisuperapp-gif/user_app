package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LabourBookingSupportRepository extends Repository<UserEntity, Long> {
    interface LabourProviderView {
        Long getLabourId();
        String getFullName();
        Long getCategoryId();
        String getCategoryName();
        BigDecimal getHourlyRate();
        BigDecimal getHalfDayRate();
        BigDecimal getFullDayRate();
    }

    interface CandidateView {
        Long getLabourId();
        BigDecimal getPriceAmount();
        BigDecimal getDistanceKm();
    }

    @Query(value = """
            SELECT
                lp.id AS labourId,
                COALESCE(up.full_name, CONCAT('Labour ', lp.id)) AS fullName,
                COALESCE(MAX(CASE WHEN lc.id = :categoryId THEN lc.id END), MIN(lc.id)) AS categoryId,
                COALESCE(MAX(CASE WHEN lc.id = :categoryId THEN lc.name END), MIN(lc.name), 'General labour') AS categoryName,
                COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HOURLY' AND lpr.is_enabled = 1 THEN lpr.hourly_price END), 0.00) AS hourlyRate,
                COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' AND lpr.is_enabled = 1 THEN lpr.half_day_price END), 0.00) AS halfDayRate,
                COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' AND lpr.is_enabled = 1 THEN lpr.full_day_price END), 0.00) AS fullDayRate
            FROM labour_profiles lp
            INNER JOIN users u ON u.id = lp.user_id
            LEFT JOIN user_profiles up ON up.user_id = u.id
            LEFT JOIN labour_skills ls ON ls.labour_id = lp.id
            LEFT JOIN labour_categories lc ON lc.id = ls.category_id
            LEFT JOIN labour_pricing lpr
                ON lpr.labour_id = lp.id
               AND (:categoryId IS NULL OR lpr.category_id = :categoryId)
            LEFT JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
            LEFT JOIN (
                SELECT provider_entity_id, COUNT(1) AS active_booking_count
                FROM bookings
                WHERE provider_entity_type = 'LABOUR'
                  AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                GROUP BY provider_entity_id
            ) active_bookings ON active_bookings.provider_entity_id = lp.id
            WHERE lp.id = :labourId
              AND lp.approval_status = 'APPROVED'
              AND lp.online_status = 1
              AND COALESCE(active_bookings.active_booking_count, 0) = 0
              AND u.id <> :userId
              AND (:categoryId IS NULL OR EXISTS (
                    SELECT 1
                    FROM labour_skills ls_filter
                    WHERE ls_filter.labour_id = lp.id
                      AND ls_filter.category_id = :categoryId
              ))
            GROUP BY lp.id, up.full_name
            HAVING MIN(
                    6371 * ACOS(
                        LEAST(
                            1,
                            COS(RADIANS(:latitude)) * COS(RADIANS(lsa.center_latitude))
                            * COS(RADIANS(lsa.center_longitude) - RADIANS(:longitude))
                            + SIN(RADIANS(:latitude)) * SIN(RADIANS(lsa.center_latitude))
                        )
                    )
                ) IS NULL
                OR MAX(COALESCE(lsa.radius_km, 0)) <= 0
                OR MIN(
                    6371 * ACOS(
                        LEAST(
                            1,
                            COS(RADIANS(:latitude)) * COS(RADIANS(lsa.center_latitude))
                            * COS(RADIANS(lsa.center_longitude) - RADIANS(:longitude))
                            + SIN(RADIANS(:latitude)) * SIN(RADIANS(lsa.center_latitude))
                        )
                    )
                ) <= MAX(COALESCE(lsa.radius_km, 0))
            LIMIT 1
            """, nativeQuery = true)
    Optional<LabourProviderView> findDirectLabourTarget(
            @Param("userId") Long userId,
            @Param("labourId") Long labourId,
            @Param("categoryId") Long categoryId,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude
    );

    @Query(value = """
            SELECT
                lp.id AS labourId,
                CASE
                    WHEN :bookingPeriod = 'HALF_DAY' THEN COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HALF_DAY' THEN lpr.half_day_price END), 0.00)
                    WHEN :bookingPeriod = 'FULL_DAY' THEN COALESCE(MAX(CASE WHEN lpr.pricing_model = 'FULL_DAY' THEN lpr.full_day_price END), 0.00)
                    ELSE COALESCE(MAX(CASE WHEN lpr.pricing_model = 'HOURLY' THEN lpr.hourly_price END), 0.00)
                END AS priceAmount,
                MIN(
                    6371 * ACOS(
                        LEAST(
                            1,
                            COS(RADIANS(:latitude)) * COS(RADIANS(lsa.center_latitude))
                            * COS(RADIANS(lsa.center_longitude) - RADIANS(:longitude))
                            + SIN(RADIANS(:latitude)) * SIN(RADIANS(lsa.center_latitude))
                        )
                    )
                ) AS distanceKm
            FROM labour_profiles lp
            INNER JOIN labour_skills ls ON ls.labour_id = lp.id
            INNER JOIN labour_pricing lpr
                ON lpr.labour_id = lp.id
               AND lpr.category_id = ls.category_id
               AND lpr.is_enabled = 1
            INNER JOIN labour_service_areas lsa ON lsa.labour_id = lp.id
            LEFT JOIN (
                SELECT provider_entity_id, COUNT(1) AS active_booking_count
                FROM bookings
                WHERE provider_entity_type = 'LABOUR'
                  AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                GROUP BY provider_entity_id
            ) active_bookings ON active_bookings.provider_entity_id = lp.id
            WHERE lp.approval_status = 'APPROVED'
              AND lp.online_status = 1
              AND COALESCE(active_bookings.active_booking_count, 0) = 0
              AND ls.category_id = :categoryId
              AND lsa.city = :city
            GROUP BY lp.id
            HAVING priceAmount > 0
               AND priceAmount <= :maxPrice
               AND (
                    distanceKm IS NULL
                    OR MAX(COALESCE(lsa.radius_km, 0)) <= 0
                    OR distanceKm <= MAX(COALESCE(lsa.radius_km, 0))
               )
            ORDER BY distanceKm ASC, priceAmount ASC, lp.id ASC
            LIMIT 50
            """, nativeQuery = true)
    List<CandidateView> findMatchingCandidates(
            @Param("categoryId") Long categoryId,
            @Param("bookingPeriod") String bookingPeriod,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("city") String city,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude
    );
}
