package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "service_states")
@Getter
@Setter
public class ServiceStateEntity {
    @Id
    private Long id;

    @Column(name = "is_active")
    private boolean active;
}
