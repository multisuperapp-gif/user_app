package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {
    @Id
    private Long id;

    @Column(name = "public_user_id")
    private String publicUserId;

    @Column(name = "phone")
    private String phone;
}
