package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.ServiceStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceStateRepository extends JpaRepository<ServiceStateEntity, Long> {
    boolean existsByIdAndActiveTrue(Long id);
}
