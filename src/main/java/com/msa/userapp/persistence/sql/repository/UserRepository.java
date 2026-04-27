package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
}
