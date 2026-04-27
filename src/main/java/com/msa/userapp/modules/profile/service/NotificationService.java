package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.NotificationListResponse;
import com.msa.userapp.modules.profile.dto.NotificationResponse;
import com.msa.userapp.persistence.sql.entity.NotificationEntity;
import com.msa.userapp.persistence.sql.repository.NotificationRepository;
import com.msa.userapp.persistence.sql.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> ALLOWED_USER_BOOKING_TYPES = Set.of(
            "BOOKING_ACCEPTED",
            "BOOKING_PAYMENT_SUCCESS",
            "BOOKING_WORK_STARTED",
            "BOOKING_COMPLETED",
            "BOOKING_CANCELLED"
    );

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, int page, int size, boolean unreadOnly) {
        validateUserExists(userId);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        List<NotificationResponse> items = notificationRepository.findVisibleNotifications(
                        userId,
                        ALLOWED_USER_BOOKING_TYPES,
                        unreadOnly,
                        PageRequest.of(safePage, safeSize)
                ).stream()
                .map(this::toResponse)
                .toList();

        long unreadCount = notificationRepository.countUnreadVisibleNotifications(userId, ALLOWED_USER_BOOKING_TYPES);

        return new NotificationListResponse(items, unreadCount);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        validateUserExists(userId);
        int updated = notificationRepository.markRead(userId, notificationId, OffsetDateTime.now());
        if (updated == 0) {
            throw new NotFoundException("Notification not found");
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        validateUserExists(userId);
        notificationRepository.markAllRead(userId, OffsetDateTime.now());
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getChannel(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPayloadJson(),
                notification.getStatus(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
