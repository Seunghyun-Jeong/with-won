package com.withwon.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;

public interface ApplicationFormVersionRepository extends JpaRepository<ApplicationFormVersion, Long> {

    Optional<ApplicationFormVersion> findTopByApplication_IdOrderByVersionNoDesc(Long applicationId);

    long countByIdInAndApplication_Incident_IdAndApplication_Incident_Customer_Id(
            Collection<Long> ids, Long incidentId, Long customerId);
}
