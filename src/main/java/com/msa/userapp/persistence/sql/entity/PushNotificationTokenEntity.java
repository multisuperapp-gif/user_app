package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "push_notification_tokens")
@Getter
@Setter
public class PushNotificationTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_device_id")
    private Long userDeviceId;

    @Column(name = "platform")
    private String platform;

    @Column(name = "push_provider")
    private String pushProvider;

    @Column(name = "push_token")
    private String pushToken;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
