package com.project.fnb.modules.payment.repository;

import com.project.fnb.modules.payment.entity.PaymentTransaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);

    @Override
    @Query("SELECT o FROM PaymentTransaction o WHERE o.id = :id")
    Optional<PaymentTransaction> findById(@Param("id") Long id);
}