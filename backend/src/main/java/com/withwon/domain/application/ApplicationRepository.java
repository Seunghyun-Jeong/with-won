package com.withwon.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByIncident_IdOrderByIdAsc(Long incidentId);
}
