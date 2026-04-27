package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserSavedItemEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSavedItemRepository extends JpaRepository<UserSavedItemEntity, Long> {
    interface SavedItemRowView {
        Long getId();
        String getTargetType();
        Long getTargetId();
        String getSavedKind();
        String getTitle();
        String getSubtitle();
        Long getShopTypeId();
        String getImageObjectKey();
        BigDecimal getPrice();
        BigDecimal getRating();
        OffsetDateTime getCreatedAt();
    }

    @Query(value = """
            SELECT
                usi.id AS id,
                usi.target_type AS targetType,
                usi.target_id AS targetId,
                usi.saved_kind AS savedKind,
                CASE usi.target_type
                    WHEN 'PRODUCT' THEN p.name
                    WHEN 'SHOP' THEN s.shop_name
                    WHEN 'LABOUR' THEN up.full_name
                    WHEN 'SERVICE_PROVIDER' THEN sup.full_name
                END AS title,
                CASE usi.target_type
                    WHEN 'PRODUCT' THEN sc.name
                    WHEN 'SHOP' THEN NULL
                    WHEN 'LABOUR' THEN CONCAT('Jobs ', lp.total_jobs_completed)
                    WHEN 'SERVICE_PROVIDER' THEN CONCAT('Jobs ', sp.total_completed_jobs)
                END AS subtitle,
                s.shop_type_id AS shopTypeId,
                CASE usi.target_type
                    WHEN 'PRODUCT' THEN product_file.object_key
                    WHEN 'SHOP' THEN NULL
                    WHEN 'LABOUR' THEN labour_file.object_key
                    WHEN 'SERVICE_PROVIDER' THEN provider_file.object_key
                END AS imageObjectKey,
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
                usi.created_at AS createdAt
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
            """, nativeQuery = true)
    List<SavedItemRowView> findSavedItems(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("savedKind") String savedKind,
            Pageable pageable
    );

    Optional<UserSavedItemEntity> findByUserIdAndTargetTypeAndTargetIdAndSavedKind(
            Long userId,
            String targetType,
            Long targetId,
            String savedKind
    );

    @Modifying
    int deleteByUserIdAndTargetTypeAndTargetIdAndSavedKind(Long userId, String targetType, Long targetId, String savedKind);

    @Query(value = "SELECT COUNT(1) FROM products WHERE id = :targetId AND is_active = 1", nativeQuery = true)
    long countActiveProductTarget(@Param("targetId") Long targetId);

    @Query(value = "SELECT COUNT(1) FROM shops WHERE id = :targetId AND approval_status = 'APPROVED'", nativeQuery = true)
    long countApprovedShopTarget(@Param("targetId") Long targetId);

    @Query(value = "SELECT COUNT(1) FROM labour_profiles WHERE id = :targetId AND approval_status = 'APPROVED'", nativeQuery = true)
    long countApprovedLabourTarget(@Param("targetId") Long targetId);

    @Query(value = "SELECT COUNT(1) FROM service_providers WHERE id = :targetId AND approval_status = 'APPROVED'", nativeQuery = true)
    long countApprovedServiceProviderTarget(@Param("targetId") Long targetId);
}
