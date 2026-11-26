package com.withwon.domain.incident;

import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerIncidentService {

    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;

    public CustomerIncidentService(IncidentRepository incidentRepository,
                                   ApplicationRepository applicationRepository) {
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
    }

    public List<Incident> findCustomerIncidents(Long customerId) {
        requireCustomerId(customerId);
        return incidentRepository.findAllByCustomer_IdOrderByCreatedAtDesc(customerId);
    }

    public Optional<Incident> findCustomerIncident(Long customerId, Long incidentId) {
        requireCustomerId(customerId);
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null.");
        }
        return incidentRepository.findByIdAndCustomer_Id(incidentId, customerId);
    }

    public List<Application> findCustomerApplications(Long customerId, Long incidentId) {
        requireCustomerId(customerId);
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null.");
        }
        if (incidentRepository.findByIdAndCustomer_Id(incidentId, customerId).isEmpty()) {
            return List.of();
        }
        return applicationRepository.findAllByIncident_IdOrderByIdAsc(incidentId);
    }

    public Page<Incident> findCustomerIncidents(Long customerId, String status, Pageable pageable) {
        requireCustomerId(customerId);
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null.");
        }
        if (status == null || status.isBlank()) {
            return incidentRepository.findAllByCustomer_Id(customerId, pageable);
        }
        return incidentRepository.findAllByCustomer_IdAndStatus(customerId, status, pageable);
    }

    private void requireCustomerId(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
    }
}
