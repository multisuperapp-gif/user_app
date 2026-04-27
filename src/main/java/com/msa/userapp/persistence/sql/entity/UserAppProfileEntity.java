package com.msa.userapp.persistence.sql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_app_profiles")
@Getter
@Setter
public class UserAppProfileEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "profile_photo_data_uri")
    private String profilePhotoDataUri;

    @Column(name = "profile_photo_object_key")
    private String profilePhotoObjectKey;

    @Column(name = "profile_photo_content_type")
    private String profilePhotoContentType;

    @Column(
            name = "gender",
            columnDefinition = "ENUM('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')"
    )
    private String gender;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "language_code")
    private String languageCode;
}
