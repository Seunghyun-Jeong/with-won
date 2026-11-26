package com.withwon.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationConsentRepository extends JpaRepository<ApplicationConsent, Long> {

    List<ApplicationConsent> findAllByIncident_IdOrderByAgreedAtDesc(Long incidentId);
}
