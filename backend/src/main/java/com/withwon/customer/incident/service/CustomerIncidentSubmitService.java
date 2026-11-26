package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.customer.incident.controller.IncidentVersionConflictException;
import com.withwon.customer.incident.controller.SubmitIncidentRequest;
import com.withwon.customer.incident.controller.SubmitIncidentResponse;
import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationConsent;
import com.withwon.domain.application.ApplicationFormVersion;
import com.withwon.domain.application.ApplicationFormVersionRepository;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
public class CustomerIncidentSubmitService {
    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationFormVersionRepository formVersionRepository;
    private final com.withwon.domain.application.ApplicationConsentRepository consentRepository;
    private final Clock clock;

    @Autowired
    public CustomerIncidentSubmitService(IncidentRepository incidentRepository,
                                         ApplicationRepository applicationRepository,
                                         ApplicationFormVersionRepository formVersionRepository,
                                         com.withwon.domain.application.ApplicationConsentRepository consentRepository) {
        this(incidentRepository, applicationRepository, formVersionRepository, consentRepository, Clock.systemUTC());
    }

    CustomerIncidentSubmitService(IncidentRepository incidentRepository,
                                  ApplicationRepository applicationRepository,
                                  ApplicationFormVersionRepository formVersionRepository,
                                  com.withwon.domain.application.ApplicationConsentRepository consentRepository,
                                  Clock clock) {
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
        this.formVersionRepository = formVersionRepository;
        this.consentRepository = consentRepository;
        this.clock = clock;
    }

    @Transactional
    public SubmitIncidentResponse submit(Long customerId, Long incidentId,
                                         SubmitIncidentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank.");
        }
        Incident incident = incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(IncidentNotFoundException::new);
        if (!request.version().equals(incident.getVersion())) {
            throw new IncidentVersionConflictException();
        }
        if (!Boolean.TRUE.equals(request.submissionConsent().agreed())) {
            throw new IllegalArgumentException("submissionConsent must be agreed.");
        }
        if (request.applicationIds().isEmpty()
                || new HashSet<>(request.applicationIds()).size() != request.applicationIds().size()) {
            throw new IllegalArgumentException("applicationIds must contain unique values.");
        }

        List<Application> applications = applicationRepository.findAllByIncident_IdOrderByIdAsc(incidentId);
        List<Application> selected = applications.stream()
                .filter(application -> request.applicationIds().contains(application.getId()))
                .toList();
        if (selected.size() != request.applicationIds().size()) {
            throw new IllegalArgumentException("All applicationIds must belong to the incident.");
        }
        if (selected.stream().anyMatch(application -> !"DRAFT".equals(application.getStatus()))) {
            throw new IllegalArgumentException("Only DRAFT applications can be submitted.");
        }

        String requestHash = request.applicationIds().stream().sorted()
                .map(String::valueOf).reduce((left, right) -> left + "\n" + right).orElseThrow();
        List<Application> previous = applicationRepository.findAllBySubmitKey(idempotencyKey);
        if (!previous.isEmpty()) {
            if (previous.stream().anyMatch(application -> !requestHash.equals(application.getSubmitRequestHash()))) {
                throw new IllegalArgumentException("Idempotency-Key was already used for another request.");
            }
            return new SubmitIncidentResponse(incident.getIncidentNumber(),
                    previous.stream().map(Application::getId).toList(), incident.getSubmittedAt(), null,
                    incident.getVersion());
        }

        LocalDateTime submittedAt = LocalDateTime.now(clock);
        for (Application application : selected) {
            ApplicationFormVersion draft = formVersionRepository
                    .findTopByApplication_IdOrderByVersionNoDesc(application.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Each application requires a saved form."));
            int nextVersion = draft.getVersionNo() + 1;
            ApplicationFormVersion submitted = formVersionRepository.save(new ApplicationFormVersion(
                    application, nextVersion, "SUBMITTED", draft.getFormData(), draft.getSchemaVersion()));
            submitted.markSubmitted(submittedAt);
            application.submit(submittedAt, idempotencyKey, requestHash);
            consentRepository.save(new ApplicationConsent(incident, "SUBMISSION",
                    request.submissionConsent().consentVersion(), submittedAt,
                    "Submission consent"));
        }
        incident.submit(submittedAt);
        return new SubmitIncidentResponse(incident.getIncidentNumber(), selected.stream()
                .map(Application::getId).toList(), submittedAt, null, incident.getVersion());
    }
}
