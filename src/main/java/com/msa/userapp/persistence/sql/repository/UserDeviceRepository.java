package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserDeviceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, Long> {
    Optional<UserDeviceEntity> findByIdAndUserId(Long id, Long userId);

    Optional<UserDeviceEntity> findByDeviceToken(String deviceToken);
}
