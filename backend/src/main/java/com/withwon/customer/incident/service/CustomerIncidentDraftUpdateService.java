package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.customer.incident.controller.IncidentVersionConflictException;
import com.withwon.customer.incident.controller.DraftUpdateNotAllowedException;
import com.withwon.customer.incident.controller.UpdateIncidentDraftRequest;
import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
public class CustomerIncidentDraftUpdateService {

    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;
    private final Clock clock;

    public CustomerIncidentDraftUpdateService(IncidentRepository incidentRepository,
                                              ApplicationRepository applicationRepository) {
        this(incidentRepository, applicationRepository, Clock.systemUTC());
    }

    CustomerIncidentDraftUpdateService(IncidentRepository incidentRepository,
                                       ApplicationRepository applicationRepository,
                                       Clock clock) {
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Transactional
    public UpdatedDraft update(Long customerId, Long incidentId, UpdateIncidentDraftRequest request) {
        Incident incident = incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(IncidentNotFoundException::new);
        if (!incident.isDraft()) {
            throw new DraftUpdateNotAllowedException();
        }
        if (!request.version().equals(incident.getVersion())) {
            throw new IncidentVersionConflictException();
        }
        validateApplicationTypes(request.applicationTypes());

        if (request.incidentDescription() != null) {
            incident.updateDescription(request.incidentDescription());
        }

        List<Application> applications = applicationRepository
                .findAllByIncident_IdOrderByIdAsc(incidentId);
        if (request.applicationTypes() != null) {
            applicationRepository.deleteAllByIncident_Id(incidentId);
            applications = request.applicationTypes().stream()
                    .map(type -> new Application(incident, type, "DRAFT"))
                    .toList();
            applications = applicationRepository.saveAll(applications);
        }
        return new UpdatedDraft(incident, applications, LocalDateTime.now(clock));
    }

    private void validateApplicationTypes(List<String> applicationTypes) {
        if (applicationTypes == null) {
            return;
        }
        if (applicationTypes.isEmpty()
                || applicationTypes.stream().anyMatch(type -> type == null || type.isBlank())
                || new HashSet<>(applicationTypes).size() != applicationTypes.size()) {
            throw new IllegalArgumentException("applicationTypes must contain unique non-blank values.");
        }
    }

    public record UpdatedDraft(Incident incident, List<Application> applications, LocalDateTime savedAt) {
    }
}
