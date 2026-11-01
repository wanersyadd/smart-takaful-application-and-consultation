package com.muqmeen.takaful.repository;

import com.muqmeen.takaful.domain.ConsultationApplication;
import com.muqmeen.takaful.domain.ContactInquiry;
import com.muqmeen.takaful.domain.PlanFinderResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlanFinderResponseRepository extends JpaRepository<PlanFinderResponse, Long> {

    Optional<PlanFinderResponse> findByToken(String token);

    Optional<PlanFinderResponse> findFirstByApplicationOrderByCreatedAtDesc(ConsultationApplication application);

    Optional<PlanFinderResponse> findFirstByInquiryOrderByCreatedAtDesc(ContactInquiry inquiry);

    @Query("select distinct p.application.id from PlanFinderResponse p where p.application is not null")
    List<Long> findLinkedApplicationIds();

    @Query("select count(p) from PlanFinderResponse p where p.application is not null or p.inquiry is not null")
    long countConverted();
}
