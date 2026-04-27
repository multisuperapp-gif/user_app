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
@Table(name = "payments")
@Getter
@Setter
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_code", nullable = false, unique = true, length = 32)
    private String paymentCode;

    @Column(
            name = "payable_type",
            nullable = false,
            columnDefinition = "ENUM('BOOKING','BOOKING_REQUEST','SHOP_ORDER')"
    )
    private String payableType;

    @Column(name = "payable_id", nullable = false)
    private Long payableId;

    @Column(name = "payer_user_id", nullable = false)
    private Long payerUserId;

    @Column(
            name = "payment_status",
            columnDefinition = "ENUM('INITIATED','PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')"
    )
    private String paymentStatus;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;
}
