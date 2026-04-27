package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.AppSettingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {
    Optional<AppSettingEntity> findBySettingKey(String settingKey);
}
