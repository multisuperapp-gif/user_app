package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.BookingRequestCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRequestCandidateRepository extends JpaRepository<BookingRequestCandidateEntity, Long> {
}
