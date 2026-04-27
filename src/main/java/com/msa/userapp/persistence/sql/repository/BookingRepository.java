package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
}
