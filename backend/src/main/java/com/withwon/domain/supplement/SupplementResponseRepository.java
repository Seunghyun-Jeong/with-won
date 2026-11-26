package com.withwon.domain.supplement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplementResponseRepository extends JpaRepository<SupplementResponse, Long> {

    Optional<SupplementResponse> findByRequestKey(String requestKey);
}
