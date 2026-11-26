package com.withwon.domain.incident;

import com.withwon.domain.application.Application;
import com.withwon.domain.application.ApplicationRepository;
import com.withwon.domain.application.ApplicationConsent;
import com.withwon.domain.application.ApplicationConsentRepository;
import com.withwon.domain.supplement.SupplementRequest;
import com.withwon.domain.supplement.SupplementRequestRepository;
import com.withwon.domain.amendment.AmendmentRequest;
import com.withwon.domain.amendment.AmendmentRequestRepository;
import com.withwon.domain.refund.RefundResultRecord;
import com.withwon.domain.refund.RefundResultRecordRepository;
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
    private final ApplicationConsentRepository applicationConsentRepository;
    private final SupplementRequestRepository supplementRequestRepository;
    private final AmendmentRequestRepository amendmentRequestRepository;
    private final RefundResultRecordRepository refundResultRecordRepository;

    public CustomerIncidentService(IncidentRepository incidentRepository,
                                   ApplicationRepository applicationRepository,
                                   ApplicationConsentRepository applicationConsentRepository,
                                   SupplementRequestRepository supplementRequestRepository,
                                   AmendmentRequestRepository amendmentRequestRepository,
                                   RefundResultRecordRepository refundResultRecordRepository) {
        this.incidentRepository = incidentRepository;
        this.applicationRepository = applicationRepository;
        this.applicationConsentRepository = applicationConsentRepository;
        this.supplementRequestRepository = supplementRequestRepository;
        this.amendmentRequestRepository = amendmentRequestRepository;
        this.refundResultRecordRepository = refundResultRecordRepository;
    }

    public List<Incident> findCustomerIncidents(Long customerId) {
        requireCustomerId(customerId);
        return incidentRepository.findAllByCustomer_IdOrderByCreatedAtDescIdDesc(customerId);
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

    public List<ApplicationConsent> findCustomerConsents(Long customerId, Long incidentId) {
        requireCustomerId(customerId);
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null.");
        }
        if (incidentRepository.findByIdAndCustomer_Id(incidentId, customerId).isEmpty()) {
            return List.of();
        }
        return applicationConsentRepository.findAllByIncident_IdOrderByAgreedAtDesc(incidentId);
    }

    public List<SupplementRequest> findCustomerSupplementRequests(Long customerId, Long incidentId) {
        requireCustomerId(customerId);
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null.");
        }
        if (incidentRepository.findByIdAndCustomer_Id(incidentId, customerId).isEmpty()) {
            return List.of();
        }
        return supplementRequestRepository.findAllByIncident_IdOrderByCreatedAtDesc(incidentId);
    }

    public List<AmendmentRequest> findCustomerAmendmentRequests(Long customerId, Long incidentId) {
        requireCustomerId(customerId);
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null.");
        }
        if (incidentRepository.findByIdAndCustomer_Id(incidentId, customerId).isEmpty()) {
            return List.of();
        }
        return amendmentRequestRepository.findAllByIncident_IdOrderByCreatedAtDesc(incidentId);
    }

    public List<RefundResultRecord> findCustomerRefundResults(Long customerId, Long incidentId) {
        requireCustomerId(customerId);
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId must not be null.");
        }
        if (incidentRepository.findByIdAndCustomer_Id(incidentId, customerId).isEmpty()) {
            return List.of();
        }
        return refundResultRecordRepository.findAllByIncident_IdOrderByVersionNoDesc(incidentId);
    }

    public Page<Incident> findCustomerIncidents(Long customerId, String status, Pageable pageable) {
        requireCustomerId(customerId);
        if (pageable == null) {
            throw new IllegalArgumentException("pageable must not be null.");
        }
        if (status == null || status.isBlank()) {
            return incidentRepository.findAllByCustomer_IdOrderByCreatedAtDescIdDesc(customerId, pageable);
        }
        return incidentRepository.findAllByCustomer_IdAndStatusOrderByCreatedAtDescIdDesc(
                customerId, status, pageable);
    }

    private void requireCustomerId(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
    }
}
