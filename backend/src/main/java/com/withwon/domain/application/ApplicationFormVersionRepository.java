package com.withwon.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationFormVersionRepository extends JpaRepository<ApplicationFormVersion, Long> {

    Optional<ApplicationFormVersion> findTopByApplication_IdOrderByVersionNoDesc(Long applicationId);
}
