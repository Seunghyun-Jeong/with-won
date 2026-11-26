package com.withwon.customer.intake.service;

import com.withwon.customer.intake.controller.CreateIncidentRequest;
import com.withwon.customer.intake.controller.IdempotencyConflictException;
import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.customer.Customer;
import com.withwon.domain.customer.CustomerRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerIncidentIntakeService {

    private static final String DRAFT_STATUS = "DRAFT";

    private final CustomerRepository customerRepository;
    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;

    public CustomerIncidentIntakeService(CustomerRepository customerRepository,
                                         IncidentRepository incidentRepository,
                                         ApplicationRepository applicationRepository) {
        this.customerRepository = customerRepository;
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public CreatedIncident create(Long customerId, CreateIncidentRequest request, String idempotencyKey) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank.");
        }
        validateApplicationTypes(request);

        String requestHash = hashRequest(request.applicationTypes());
        var existing = incidentRepository.findByCustomer_IdAndSubmitKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            Incident incident = existing.get();
            if (!requestHash.equals(incident.getSubmitRequestHash())) {
                throw new IdempotencyConflictException();
            }
            return new CreatedIncident(incident,
                    applicationRepository.findAllByIncident_IdOrderByIdAsc(incident.getId()));
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(EntityNotFoundException::new);
        Incident incident = new Incident(customer, "INC-" + UUID.randomUUID(), DRAFT_STATUS, null);
        incident.setSubmitRequest(idempotencyKey, requestHash);
        Incident savedIncident = incidentRepository.save(incident);

        List<Application> applications = request.applicationTypes().stream()
                .map(type -> new Application(savedIncident, type, DRAFT_STATUS))
                .toList();
        applications = applicationRepository.saveAll(applications);
        return new CreatedIncident(savedIncident, applications);
    }

    private void validateApplicationTypes(CreateIncidentRequest request) {
        if (request == null || request.applicationTypes() == null || request.applicationTypes().isEmpty()) {
            throw new IllegalArgumentException("applicationTypes must not be empty.");
        }
        if (new HashSet<>(request.applicationTypes()).size() != request.applicationTypes().size()) {
            throw new IllegalArgumentException("applicationTypes must not contain duplicates.");
        }
    }

    private String hashRequest(List<String> applicationTypes) {
        String canonical = applicationTypes.stream().sorted().reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record CreatedIncident(Incident incident, List<Application> applications) {
    }
}
