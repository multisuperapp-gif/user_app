package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "booking_requests")
@Getter
@Setter
public class BookingRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code")
    private String requestCode;

    @Column(
            name = "booking_type",
            columnDefinition = "ENUM('LABOUR','SERVICE')"
    )
    private String bookingType;

    @Column(
            name = "request_mode",
            columnDefinition = "ENUM('DIRECT','BROADCAST')"
    )
    private String requestMode;

    @Column(
            name = "request_status",
            columnDefinition = "ENUM('OPEN','ACCEPTED','EXPIRED','CANCELLED','CONVERTED_TO_BOOKING')"
    )
    private String requestStatus;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "subcategory_id")
    private Long subcategoryId;

    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "price_max_amount")
    private BigDecimal priceMaxAmount;

    @Column(name = "search_latitude")
    private BigDecimal searchLatitude;

    @Column(name = "search_longitude")
    private BigDecimal searchLongitude;
}
