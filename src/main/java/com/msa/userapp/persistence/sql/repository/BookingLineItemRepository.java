package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.BookingLineItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingLineItemRepository extends JpaRepository<BookingLineItemEntity, Long> {
}
