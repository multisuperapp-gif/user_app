package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.NotificationListResponse;
import com.msa.userapp.modules.profile.dto.NotificationResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NotificationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, int page, int size, boolean unreadOnly) {
        validateUserExists(userId);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("unreadOnly", unreadOnly)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);

        List<NotificationResponse> items = jdbcTemplate.query("""
                SELECT
                    n.id,
                    n.channel,
                    n.notification_type,
                    n.title,
                    n.body,
                    n.payload_json,
                    n.status,
                    n.sent_at,
                    n.read_at,
                    n.created_at
                FROM notifications n
                WHERE n.user_id = :userId
                  AND (:unreadOnly = false OR n.read_at IS NULL)
                ORDER BY CASE WHEN n.read_at IS NULL THEN 0 ELSE 1 END, n.created_at DESC, n.id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new NotificationResponse(
                rs.getLong("id"),
                rs.getString("channel"),
                rs.getString("notification_type"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("payload_json"),
                rs.getString("status"),
                rs.getObject("sent_at", OffsetDateTime.class),
                rs.getObject("read_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ));

        Long unreadCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM notifications
                WHERE user_id = :userId
                  AND read_at IS NULL
                """, Map.of("userId", userId), Long.class);

        return new NotificationListResponse(items, unreadCount == null ? 0 : unreadCount);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        validateUserExists(userId);
        int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                WHERE id = :notificationId
                  AND user_id = :userId
                """, new MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("userId", userId));
        if (updated == 0) {
            throw new NotFoundException("Notification not found");
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        validateUserExists(userId);
        jdbcTemplate.update("""
                UPDATE notifications
                SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                WHERE user_id = :userId
                  AND read_at IS NULL
                """, Map.of("userId", userId));
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
}
