package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.PushNotificationTokenEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushNotificationTokenRepository extends JpaRepository<PushNotificationTokenEntity, Long> {
    Optional<PushNotificationTokenEntity> findByIdAndUserId(Long id, Long userId);

    Optional<PushNotificationTokenEntity> findByPushToken(String pushToken);

    @Modifying
    @Query("""
            update PushNotificationTokenEntity token
            set token.active = false,
                token.updatedAt = :updatedAt
            where token.userId = :userId
              and token.pushToken = :pushToken
            """)
    int deactivateByUserIdAndPushToken(
            @Param("userId") Long userId,
            @Param("pushToken") String pushToken,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
