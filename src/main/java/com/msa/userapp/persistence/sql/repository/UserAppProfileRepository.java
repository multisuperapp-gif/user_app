package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserAppProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAppProfileRepository extends JpaRepository<UserAppProfileEntity, Long> {
    java.util.Optional<UserAppProfileEntity> findByProfilePhotoObjectKey(String profilePhotoObjectKey);
}
