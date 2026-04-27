package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.PushTokenDeactivateRequest;
import com.msa.userapp.modules.profile.dto.PushTokenRegisterRequest;
import com.msa.userapp.modules.profile.dto.PushTokenResponse;
import com.msa.userapp.persistence.sql.entity.PushNotificationTokenEntity;
import com.msa.userapp.persistence.sql.entity.UserDeviceEntity;
import com.msa.userapp.persistence.sql.repository.PushNotificationTokenRepository;
import com.msa.userapp.persistence.sql.repository.UserDeviceRepository;
import com.msa.userapp.persistence.sql.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushTokenService {
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PushNotificationTokenRepository pushNotificationTokenRepository;

    public PushTokenService(
            UserRepository userRepository,
            UserDeviceRepository userDeviceRepository,
            PushNotificationTokenRepository pushNotificationTokenRepository
    ) {
        this.userRepository = userRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.pushNotificationTokenRepository = pushNotificationTokenRepository;
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
        int updated = pushNotificationTokenRepository.deactivateByUserIdAndPushToken(
                userId,
                request.pushToken().trim(),
                OffsetDateTime.now()
        );
        if (updated == 0) {
            throw new NotFoundException("Push token not found");
        }
    }

    private Long resolveUserDeviceId(Long userId, PushTokenRegisterRequest request) {
        if (request.userDeviceId() != null) {
            UserDeviceEntity existing = userDeviceRepository.findByIdAndUserId(request.userDeviceId(), userId)
                    .orElseThrow(() -> new NotFoundException("User device not found"));
            updateExistingUserDevice(existing, userId, request);
            return existing.getId();
        }

        if (request.deviceToken() == null || request.deviceToken().isBlank()) {
            return null;
        }

        return upsertUserDevice(userId, request);
    }

    private void updateExistingUserDevice(Long userDeviceId, Long userId, PushTokenRegisterRequest request) {
        UserDeviceEntity existing = userDeviceRepository.findByIdAndUserId(userDeviceId, userId)
                .orElseThrow(() -> new NotFoundException("User device not found"));
        updateExistingUserDevice(existing, userId, request);
    }

    private void updateExistingUserDevice(UserDeviceEntity device, Long userId, PushTokenRegisterRequest request) {
        device.setUserId(userId);
        device.setDeviceType(normalizePlatform(request.platform()));
        device.setDeviceToken(trimToNull(request.deviceToken()));
        device.setAppVersion(trimToNull(request.appVersion()));
        device.setOsVersion(trimToNull(request.osVersion()));
        device.setLastSeenAt(OffsetDateTime.now());
        userDeviceRepository.save(device);
    }

    private Long upsertUserDevice(Long userId, PushTokenRegisterRequest request) {
        UserDeviceEntity device = userDeviceRepository.findByDeviceToken(request.deviceToken().trim()).orElseGet(UserDeviceEntity::new);
        device.setUserId(userId);
        device.setDeviceType(normalizePlatform(request.platform()));
        device.setDeviceToken(request.deviceToken().trim());
        device.setAppVersion(trimToNull(request.appVersion()));
        device.setOsVersion(trimToNull(request.osVersion()));
        device.setLastSeenAt(OffsetDateTime.now());
        return userDeviceRepository.save(device).getId();
    }

    private Long upsertPushToken(Long userId, Long userDeviceId, PushTokenRegisterRequest request) {
        PushNotificationTokenEntity token = pushNotificationTokenRepository.findByPushToken(request.pushToken().trim())
                .orElseGet(PushNotificationTokenEntity::new);
        token.setUserId(userId);
        token.setUserDeviceId(userDeviceId);
        token.setPlatform(normalizePlatform(request.platform()));
        token.setPushProvider(normalizeProvider(request.pushProvider()));
        token.setPushToken(request.pushToken().trim());
        token.setActive(true);
        token.setLastSeenAt(OffsetDateTime.now());
        token.setUpdatedAt(OffsetDateTime.now());
        return pushNotificationTokenRepository.save(token).getId();
    }

    private PushTokenResponse fetchToken(Long userId, Long tokenId) {
        PushNotificationTokenEntity token = pushNotificationTokenRepository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new NotFoundException("Push token not found"));
        return new PushTokenResponse(
                token.getId(),
                token.getUserDeviceId(),
                token.getPlatform(),
                token.getPushProvider(),
                token.getPushToken(),
                token.isActive(),
                token.getLastSeenAt()
        );
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
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
