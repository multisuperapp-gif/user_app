package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.ServiceCountryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCountryRepository extends JpaRepository<ServiceCountryEntity, Long> {
    boolean existsByIdAndActiveTrue(Long id);
}
