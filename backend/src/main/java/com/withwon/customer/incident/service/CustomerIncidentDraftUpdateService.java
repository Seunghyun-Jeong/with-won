package com.withwon.customer.incident.service;

import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.customer.incident.controller.IncidentVersionConflictException;
import com.withwon.customer.incident.controller.DraftUpdateNotAllowedException;
import com.withwon.customer.incident.controller.UpdateIncidentDraftRequest;
import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationFormVersion;
import com.withwon.domain.application.ApplicationFormVersionRepository;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ApplicationFormVersionRepository applicationFormVersionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CustomerIncidentDraftUpdateService(IncidentRepository incidentRepository,
                                              ApplicationRepository applicationRepository) {
        this(incidentRepository, applicationRepository, null,
                new ObjectMapper().registerModule(new JavaTimeModule()), Clock.systemUTC());
    }

    CustomerIncidentDraftUpdateService(IncidentRepository incidentRepository,
                                       ApplicationRepository applicationRepository,
                                       Clock clock) {
        this(incidentRepository, applicationRepository, null,
                new ObjectMapper().registerModule(new JavaTimeModule()), clock);
    }

    public CustomerIncidentDraftUpdateService(IncidentRepository incidentRepository,
                                              ApplicationRepository applicationRepository,
                                              ApplicationFormVersionRepository applicationFormVersionRepository,
                                              ObjectMapper objectMapper) {
        this(incidentRepository, applicationRepository, applicationFormVersionRepository,
                objectMapper, Clock.systemUTC());
    }

    private CustomerIncidentDraftUpdateService(IncidentRepository incidentRepository,
                                               ApplicationRepository applicationRepository,
                                               ApplicationFormVersionRepository applicationFormVersionRepository,
                                               ObjectMapper objectMapper,
                                               Clock clock) {
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
        this.applicationFormVersionRepository = applicationFormVersionRepository;
        this.objectMapper = objectMapper;
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
        if (request.reliefApplicationForm() != null) {
            saveFormVersions(incident, applications, request.reliefApplicationForm());
        }
        return new UpdatedDraft(incident, applications, LocalDateTime.now(clock));
    }

    private void saveFormVersions(Incident incident, List<Application> applications,
                                  com.withwon.customer.incident.controller.ReliefApplicationFormRequest form) {
        if (applicationFormVersionRepository == null) {
            throw new IllegalStateException("Application form version repository is not configured.");
        }
        var applicant = form.applicant();
        var customer = incident.getCustomer();
        var data = new SavedReliefApplicationForm(
                new SavedApplicant(
                        applicant != null && applicant.name() != null ? applicant.name() : customer.getName(),
                        applicant == null ? null : applicant.birthDate(),
                        applicant == null ? null : applicant.address(),
                        applicant != null && applicant.phone() != null ? applicant.phone() : customer.getPhone(),
                        applicant == null ? null : applicant.email()),
                form.victimAccountTransfers(), form.fraudAccountDeposits(), form.refundAssetAccount(),
                form.applicationReason(), form.refundAccountNumber(), "우리은행",
                null, null, null);
        try {
            String formData = objectMapper.writeValueAsString(data);
            for (Application application : applications) {
                int nextVersion = applicationFormVersionRepository
                        .findTopByApplication_IdOrderByVersionNoDesc(application.getId())
                        .map(version -> version.getVersionNo() + 1)
                        .orElse(1);
                applicationFormVersionRepository.save(new ApplicationFormVersion(
                        application, nextVersion, "DRAFT", formData, "RELIEF_APPLICATION_V1"));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize relief application form.", e);
        }
    }

    private record SavedReliefApplicationForm(
            SavedApplicant applicant,
            Object victimAccountTransfers,
            Object fraudAccountDeposits,
            Object refundAssetAccount,
            String applicationReason,
            String refundAccountNumber,
            String recipientFinancialCompany,
            String receiptNumber,
            String receiptDate,
            String applicationDate
    ) {
    }

    private record SavedApplicant(String name, String birthDate, String address,
                                  String phone, String email) {
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
