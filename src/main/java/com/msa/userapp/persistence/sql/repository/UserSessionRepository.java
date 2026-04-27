package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {
    Optional<UserSessionEntity> findByIdAndUserId(Long id, Long userId);
}
