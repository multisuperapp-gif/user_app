package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.PushTokenDeactivateRequest;
import com.msa.userapp.modules.profile.dto.PushTokenRegisterRequest;
import com.msa.userapp.modules.profile.dto.PushTokenResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushTokenService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PushTokenService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PushTokenResponse register(Long userId, PushTokenRegisterRequest request) {
        validateUserExists(userId);
        Long userDeviceId = resolveUserDeviceId(userId, request);
        Long tokenId = upsertPushToken(userId, userDeviceId, request);
        return fetchToken(userId, tokenId);
    }

    @Transactional
    public void deactivate(Long userId, PushTokenDeactivateRequest request) {
        validateUserExists(userId);
        int updated = jdbcTemplate.update("""
                UPDATE push_notification_tokens
                SET is_active = 0,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId
                  AND push_token = :pushToken
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("pushToken", request.pushToken().trim()));
        if (updated == 0) {
            throw new NotFoundException("Push token not found");
        }
    }

    private Long resolveUserDeviceId(Long userId, PushTokenRegisterRequest request) {
        if (request.userDeviceId() != null) {
            Long existing = jdbcTemplate.query("""
                    SELECT id
                    FROM user_devices
                    WHERE id = :userDeviceId
                      AND user_id = :userId
                    """, new MapSqlParameterSource()
                    .addValue("userDeviceId", request.userDeviceId())
                    .addValue("userId", userId), rs -> rs.next() ? rs.getLong("id") : null);
            if (existing == null) {
                throw new NotFoundException("User device not found");
            }
            updateExistingUserDevice(existing, userId, request);
            return existing;
        }

        if (request.deviceToken() == null || request.deviceToken().isBlank()) {
            return null;
        }

        return upsertUserDevice(userId, request);
    }

    private void updateExistingUserDevice(Long userDeviceId, Long userId, PushTokenRegisterRequest request) {
        jdbcTemplate.update("""
                UPDATE user_devices
                SET user_id = :userId,
                    device_type = :deviceType,
                    device_token = :deviceToken,
                    app_version = :appVersion,
                    os_version = :osVersion,
                    last_seen_at = CURRENT_TIMESTAMP
                WHERE id = :userDeviceId
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("userDeviceId", userDeviceId)
                .addValue("deviceType", normalizePlatform(request.platform()))
                .addValue("deviceToken", trimToNull(request.deviceToken()))
                .addValue("appVersion", trimToNull(request.appVersion()))
                .addValue("osVersion", trimToNull(request.osVersion())));
    }

    private Long upsertUserDevice(Long userId, PushTokenRegisterRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_devices (
                        user_id,
                        device_type,
                        device_token,
                        app_version,
                        os_version,
                        last_seen_at
                    ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        id = LAST_INSERT_ID(id),
                        user_id = VALUES(user_id),
                        device_type = VALUES(device_type),
                        app_version = VALUES(app_version),
                        os_version = VALUES(os_version),
                        last_seen_at = CURRENT_TIMESTAMP
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, normalizePlatform(request.platform()));
            statement.setString(3, request.deviceToken().trim());
            statement.setString(4, trimToNull(request.appVersion()));
            statement.setString(5, trimToNull(request.osVersion()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create or update user device");
        }
        return key.longValue();
    }

    private Long upsertPushToken(Long userId, Long userDeviceId, PushTokenRegisterRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.getJdbcTemplate().update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO push_notification_tokens (
                        user_id,
                        user_device_id,
                        platform,
                        push_provider,
                        push_token,
                        is_active,
                        last_seen_at
                    ) VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        id = LAST_INSERT_ID(id),
                        user_id = VALUES(user_id),
                        user_device_id = VALUES(user_device_id),
                        platform = VALUES(platform),
                        push_provider = VALUES(push_provider),
                        is_active = 1,
                        last_seen_at = CURRENT_TIMESTAMP
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            if (userDeviceId == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, userDeviceId);
            }
            statement.setString(3, normalizePlatform(request.platform()));
            statement.setString(4, normalizeProvider(request.pushProvider()));
            statement.setString(5, request.pushToken().trim());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create or update push token");
        }
        return key.longValue();
    }

    private PushTokenResponse fetchToken(Long userId, Long tokenId) {
        PushTokenResponse response = jdbcTemplate.query("""
                SELECT
                    id,
                    user_device_id,
                    platform,
                    push_provider,
                    push_token,
                    is_active,
                    last_seen_at
                FROM push_notification_tokens
                WHERE id = :tokenId
                  AND user_id = :userId
                """, new MapSqlParameterSource()
                .addValue("tokenId", tokenId)
                .addValue("userId", userId), rs -> {
            if (!rs.next()) {
                return null;
            }
            return new PushTokenResponse(
                    rs.getLong("id"),
                    rs.getObject("user_device_id", Long.class),
                    rs.getString("platform"),
                    rs.getString("push_provider"),
                    rs.getString("push_token"),
                    rs.getBoolean("is_active"),
                    rs.getObject("last_seen_at", OffsetDateTime.class)
            );
        });
        if (response == null) {
            throw new NotFoundException("Push token not found");
        }
        return response;
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

    private String normalizePlatform(String platform) {
        return platform.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProvider(String pushProvider) {
        return pushProvider.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
