package com.withwon.domain.incident;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;

    public IncidentQueryService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
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

    private void requireCustomerId(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
    }
}
