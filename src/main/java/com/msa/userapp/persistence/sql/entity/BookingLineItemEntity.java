package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "booking_line_items")
@Getter
@Setter
public class BookingLineItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_ref_type")
    private String serviceRefType;

    @Column(name = "service_ref_id")
    private Long serviceRefId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "price_snapshot")
    private BigDecimal priceSnapshot;
}
