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
@Table(name = "bookings")
@Getter
@Setter
public class BookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_request_id")
    private Long bookingRequestId;

    @Column(name = "booking_code")
    private String bookingCode;

    @Column(
            name = "booking_type",
            columnDefinition = "ENUM('LABOUR','SERVICE')"
    )
    private String bookingType;

    @Column(name = "user_id")
    private Long userId;

    @Column(
            name = "provider_entity_type",
            columnDefinition = "ENUM('SERVICE_PROVIDER','LABOUR')"
    )
    private String providerEntityType;

    @Column(name = "provider_entity_id")
    private Long providerEntityId;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @Column(
            name = "booking_status",
            columnDefinition = "ENUM('CREATED','ACCEPTED','PAYMENT_PENDING','PAYMENT_COMPLETED','ARRIVED','IN_PROGRESS','COMPLETED','CANCELLED','DISPUTED')"
    )
    private String bookingStatus;

    @Column(
            name = "payment_status",
            columnDefinition = "ENUM('UNPAID','PENDING','PAID','FAILED','REFUNDED','PARTIALLY_REFUNDED')"
    )
    private String paymentStatus;

    @Column(name = "subtotal_amount")
    private BigDecimal subtotalAmount;

    @Column(name = "total_estimated_amount")
    private BigDecimal totalEstimatedAmount;

    @Column(name = "currency_code")
    private String currencyCode;
}
