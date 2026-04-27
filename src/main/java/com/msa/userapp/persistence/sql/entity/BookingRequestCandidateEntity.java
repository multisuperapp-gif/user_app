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
@Table(name = "booking_request_candidates")
@Getter
@Setter
public class BookingRequestCandidateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private Long requestId;

    @Column(
            name = "provider_entity_type",
            columnDefinition = "ENUM('SERVICE_PROVIDER','LABOUR')"
    )
    private String providerEntityType;

    @Column(name = "provider_entity_id")
    private Long providerEntityId;

    @Column(
            name = "candidate_status",
            columnDefinition = "ENUM('PENDING','ACCEPTED','REJECTED','EXPIRED','CLOSED')"
    )
    private String candidateStatus;

    @Column(name = "quoted_price_amount")
    private BigDecimal quotedPriceAmount;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
