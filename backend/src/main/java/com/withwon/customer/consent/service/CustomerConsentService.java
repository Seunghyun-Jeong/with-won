package com.withwon.customer.consent.service;

import com.withwon.customer.application.config.CustomerApplicationProperties;
import com.withwon.customer.consent.controller.ConsentSubmissionRequest;
import com.withwon.customer.consent.controller.ConsentSubmissionResponse;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.application.ApplicationConsent;
import com.withwon.domain.application.ApplicationConsentRepository;
import com.withwon.domain.incident.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerConsentService {

    private final IncidentRepository incidentRepository;
    private final ApplicationConsentRepository consentRepository;
    private final CustomerApplicationProperties properties;

    public CustomerConsentService(IncidentRepository incidentRepository,
                                  ApplicationConsentRepository consentRepository,
                                  CustomerApplicationProperties properties) {
        this.incidentRepository = incidentRepository;
        this.consentRepository = consentRepository;
        this.properties = properties;
    }

    @Transactional
    public ConsentSubmissionResponse submit(Long customerId, Long incidentId,
                                            ConsentSubmissionRequest request) {
        var incident = incidentRepository.findByIdAndCustomer_Id(incidentId, customerId)
                .orElseThrow(IncidentNotFoundException::new);
        if (request == null || request.consents() == null) {
            throw new IllegalArgumentException("consents must not be null.");
        }
        Map<String, CustomerApplicationProperties.ConsentForm> configured =
                properties.getConsentForms().stream().collect(Collectors.toMap(
                        CustomerApplicationProperties.ConsentForm::getConsentType,
                        Function.identity()));
        Map<String, ConsentSubmissionRequest.ConsentItem> submitted = request.consents().stream()
                .collect(Collectors.toMap(ConsentSubmissionRequest.ConsentItem::consentType,
                        Function.identity(), (left, right) -> {
                            throw new IllegalArgumentException("Duplicate consentType.");
                        }));
        if (!submitted.keySet().equals(configured.keySet())) {
            throw new IllegalArgumentException("All configured consent types must be submitted.");
        }
        LocalDateTime agreedAt = LocalDateTime.now();
        for (var entry : configured.entrySet()) {
            var consent = submitted.get(entry.getKey());
            var form = entry.getValue();
            if (!consent.agreed() || !form.getConsentVersion().equals(consent.consentVersion())) {
                throw new IllegalArgumentException("Consent is not agreed or version is invalid.");
            }
            consentRepository.save(new ApplicationConsent(
                    incident, form.getConsentType(), form.getConsentVersion(), agreedAt, form.getContent()));
        }
        return new ConsentSubmissionResponse(
                submitted.values().stream()
                        .map(item -> new ConsentSubmissionResponse.ConsentItemResponse(
                                item.consentType(), item.consentVersion(), item.agreed()))
                        .toList(),
                true,
                agreedAt,
                incident.getVersion());
    }
}
