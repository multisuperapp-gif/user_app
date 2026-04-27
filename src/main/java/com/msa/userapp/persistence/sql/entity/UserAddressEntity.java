package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_addresses")
@Getter
@Setter
public class UserAddressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "label")
    private String label;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "landmark")
    private String landmark;

    @Column(name = "city")
    private String city;

    @Column(name = "state_id")
    private Long stateId;

    @Column(name = "state")
    private String state;

    @Column(name = "country_id")
    private Long countryId;

    @Column(name = "country")
    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "latitude", columnDefinition = "DOUBLE")
    private BigDecimal latitude;

    @Column(name = "longitude", columnDefinition = "DOUBLE")
    private BigDecimal longitude;

    @Column(name = "is_default")
    private boolean defaultAddress;

    @Column(name = "is_booking_temp")
    private boolean bookingTemp;

    @Column(name = "is_hidden")
    private boolean hidden;

    @Column(name = "address_scope")
    private String addressScope;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
