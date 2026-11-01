package com.muqmeen.takaful.repository;

import com.muqmeen.takaful.domain.ApplicationStatus;
import com.muqmeen.takaful.domain.ConsultationApplication;
import com.muqmeen.takaful.domain.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    Optional<Quotation> findByApplication(ConsultationApplication application);

    @Query("select coalesce(sum(i.amount), 0) from QuotationItem i where i.selected = true and i.quotation.application.status in :statuses")
    BigDecimal sumSelectedAmountForApplicationStatuses(@Param("statuses") List<ApplicationStatus> statuses);
}
