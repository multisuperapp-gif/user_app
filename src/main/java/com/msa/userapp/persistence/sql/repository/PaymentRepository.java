package com.msa.userapp.persistence.sql.repository;

import com.msa.userapp.persistence.sql.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByPaymentCodeAndPayerUserId(String paymentCode, Long payerUserId);
}
