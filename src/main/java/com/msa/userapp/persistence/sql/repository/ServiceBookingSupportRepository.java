package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserEntity;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ServiceBookingSupportRepository extends Repository<UserEntity, Long> {
    interface ServiceTargetView {
        Long getProviderId();
        String getProviderName();
        Long getProviderServiceId();
        String getServiceName();
        BigDecimal getVisitingCharge();
    }

    interface ServiceRequestTargetView {
        Long getProviderId();
        String getProviderName();
        Long getProviderServiceId();
        String getServiceName();
        BigDecimal getVisitingCharge();
    }

    @Query(value = """
            SELECT
                sp.id AS providerId,
                COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS providerName,
                ps.id AS providerServiceId,
                ps.service_name AS serviceName,
                COALESCE(ppr.visiting_charge, 0.00) AS visitingCharge
            FROM service_providers sp
            INNER JOIN users u ON u.id = sp.user_id
            LEFT JOIN user_profiles up ON up.user_id = u.id
            INNER JOIN provider_services ps
                ON ps.provider_id = sp.id
               AND ps.is_active = 1
            INNER JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
            LEFT JOIN provider_pricing_rules ppr ON ppr.provider_service_id = ps.id
            LEFT JOIN (
                SELECT provider_entity_id, COUNT(1) AS active_booking_count
                FROM bookings
                WHERE provider_entity_type = 'SERVICE_PROVIDER'
                  AND booking_status IN ('ACCEPTED', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                GROUP BY provider_entity_id
            ) active_bookings ON active_bookings.provider_entity_id = sp.id
            WHERE sp.id = :providerId
              AND sp.approval_status = 'APPROVED'
              AND sp.online_status = 1
              AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0
              AND u.id <> :userId
              AND (:categoryId IS NULL OR psc.category_id = :categoryId)
              AND (:subcategoryId IS NULL OR psc.id = :subcategoryId)
            ORDER BY
                CASE WHEN :subcategoryId IS NOT NULL AND psc.id = :subcategoryId THEN 0 ELSE 1 END,
                CASE WHEN :categoryId IS NOT NULL AND psc.category_id = :categoryId THEN 0 ELSE 1 END,
                ps.id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ServiceTargetView> findDirectServiceTarget(
            @Param("userId") Long userId,
            @Param("providerId") Long providerId,
            @Param("categoryId") Long categoryId,
            @Param("subcategoryId") Long subcategoryId
    );

    @Query(value = """
            SELECT
                sp.id AS providerId,
                COALESCE(up.full_name, CONCAT('Provider ', sp.id)) AS providerName,
                ps.id AS providerServiceId,
                ps.service_name AS serviceName,
                COALESCE(ppr.visiting_charge, 0.00) AS visitingCharge
            FROM service_providers sp
            INNER JOIN users u ON u.id = sp.user_id
            LEFT JOIN user_profiles up ON up.user_id = u.id
            INNER JOIN provider_services ps
                ON ps.provider_id = sp.id
               AND ps.is_active = 1
            INNER JOIN provider_subcategories psc ON psc.id = ps.subcategory_id
            LEFT JOIN provider_pricing_rules ppr ON ppr.provider_service_id = ps.id
            LEFT JOIN provider_service_areas psa ON psa.provider_id = sp.id
            LEFT JOIN (
                SELECT provider_entity_id, COUNT(1) AS active_booking_count
                FROM bookings
                WHERE provider_entity_type = 'SERVICE_PROVIDER'
                  AND booking_status IN ('ACCEPTED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED', 'ARRIVED', 'IN_PROGRESS')
                GROUP BY provider_entity_id
            ) active_bookings ON active_bookings.provider_entity_id = sp.id
            WHERE sp.id = :providerId
              AND sp.approval_status = 'APPROVED'
              AND sp.online_status = 1
              AND GREATEST(COALESCE(sp.available_service_men, 0) - COALESCE(active_bookings.active_booking_count, 0), 0) > 0
              AND u.id <> :userId
              AND (:categoryId IS NULL OR psc.category_id = :categoryId)
              AND (:subcategoryId IS NULL OR psc.id = :subcategoryId)
            GROUP BY sp.id, up.full_name, ps.id, ps.service_name, ppr.visiting_charge
            HAVING MIN(
                    6371 * ACOS(
                        LEAST(
                            1,
                            COS(RADIANS(:latitude)) * COS(RADIANS(psa.center_latitude))
                            * COS(RADIANS(psa.center_longitude) - RADIANS(:longitude))
                            + SIN(RADIANS(:latitude)) * SIN(RADIANS(psa.center_latitude))
                        )
                    )
                ) IS NULL
                OR MAX(COALESCE(psa.radius_km, 0)) <= 0
                OR MIN(
                    6371 * ACOS(
                        LEAST(
                            1,
                            COS(RADIANS(:latitude)) * COS(RADIANS(psa.center_latitude))
                            * COS(RADIANS(psa.center_longitude) - RADIANS(:longitude))
                            + SIN(RADIANS(:latitude)) * SIN(RADIANS(psa.center_latitude))
                        )
                    )
                ) <= MAX(COALESCE(psa.radius_km, 0))
            ORDER BY
                CASE WHEN :subcategoryId IS NOT NULL AND psc.id = :subcategoryId THEN 0 ELSE 1 END,
                CASE WHEN :categoryId IS NOT NULL AND psc.category_id = :categoryId THEN 0 ELSE 1 END,
                MIN(
                    6371 * ACOS(
                        LEAST(
                            1,
                            COS(RADIANS(:latitude)) * COS(RADIANS(psa.center_latitude))
                            * COS(RADIANS(psa.center_longitude) - RADIANS(:longitude))
                            + SIN(RADIANS(:latitude)) * SIN(RADIANS(psa.center_latitude))
                        )
                    )
                ) ASC,
                ps.id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ServiceRequestTargetView> findRequestServiceTarget(
            @Param("userId") Long userId,
            @Param("providerId") Long providerId,
            @Param("categoryId") Long categoryId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude
    );
}
