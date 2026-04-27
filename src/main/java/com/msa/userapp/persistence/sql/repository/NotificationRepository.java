package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    @Query("""
            select n
            from NotificationEntity n
            where n.userId = :userId
              and (
                    n.notificationType not like 'BOOKING\\_%' escape '\\'
                    or n.notificationType in :allowedTypes
              )
              and (:unreadOnly = false or n.readAt is null)
            order by case when n.readAt is null then 0 else 1 end, n.createdAt desc, n.id desc
            """)
    List<NotificationEntity> findVisibleNotifications(
            @Param("userId") Long userId,
            @Param("allowedTypes") Collection<String> allowedTypes,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );

    @Query("""
            select count(n)
            from NotificationEntity n
            where n.userId = :userId
              and (
                    n.notificationType not like 'BOOKING\\_%' escape '\\'
                    or n.notificationType in :allowedTypes
              )
              and n.readAt is null
            """)
    long countUnreadVisibleNotifications(@Param("userId") Long userId, @Param("allowedTypes") Collection<String> allowedTypes);

    @Modifying
    @Query("""
            update NotificationEntity n
            set n.readAt = :readAt
            where n.id = :notificationId
              and n.userId = :userId
              and n.readAt is null
            """)
    int markRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId, @Param("readAt") OffsetDateTime readAt);

    @Modifying
    @Query("""
            update NotificationEntity n
            set n.readAt = :readAt
            where n.userId = :userId
              and n.readAt is null
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);
}
