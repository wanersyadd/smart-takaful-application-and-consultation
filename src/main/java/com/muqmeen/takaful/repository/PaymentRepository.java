package com.muqmeen.takaful.repository;

import com.muqmeen.takaful.domain.Customer;
import com.muqmeen.takaful.domain.Payment;
import com.muqmeen.takaful.domain.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Payment findByBillCode(String billCode);

    Payment findByExternalReferenceNo(String externalReferenceNo);

    Payment findByQuotation(Quotation quotation);

    List<Payment> findAllByCustomerOrderByCreatedAtDesc(Customer customer);

    @Query("select coalesce(sum(p.amountCents), 0) from Payment p where p.status = 'PAID' and p.updatedAt >= :from and p.updatedAt < :to")
    long sumPaidCentsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
