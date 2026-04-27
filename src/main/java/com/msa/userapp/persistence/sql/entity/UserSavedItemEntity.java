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
@Table(name = "user_saved_items")
@Getter
@Setter
public class UserSavedItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(
            name = "target_type",
            nullable = false,
            columnDefinition = "ENUM('SHOP','PRODUCT','LABOUR','SERVICE_PROVIDER')"
    )
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(
            name = "saved_kind",
            nullable = false,
            columnDefinition = "ENUM('WISHLIST','FAVOURITE')"
    )
    private String savedKind;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
