package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.BookingRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRequestRepository extends JpaRepository<BookingRequestEntity, Long> {
}
