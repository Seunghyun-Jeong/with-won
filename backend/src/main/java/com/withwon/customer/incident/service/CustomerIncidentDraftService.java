package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.DraftDeletionNotAllowedException;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class CustomerIncidentDraftService {

    private static final String DRAFT_STATUS = "DRAFT";

    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;

    public CustomerIncidentDraftService(IncidentRepository incidentRepository,
                                        ApplicationRepository applicationRepository) {
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public void deleteDraft(Long customerId, Long incidentId) {
        Incident incident = incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(IncidentNotFoundException::new);
        if (!DRAFT_STATUS.equals(incident.getStatus())) {
            throw new DraftDeletionNotAllowedException();
        }

        applicationRepository.deleteAllByIncident_Id(incidentId);
        incidentRepository.delete(incident);
    }
}
