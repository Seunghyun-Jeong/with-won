package com.withwon.customer.evidence.service;

import com.withwon.customer.incident.controller.DraftUpdateNotAllowedException;
import com.withwon.customer.incident.controller.IncidentNotFoundException;
import com.withwon.domain.evidence.EvidenceFile;
import com.withwon.domain.evidence.EvidenceFileRepository;
import com.withwon.domain.evidence.EvidenceVersionRepository;
import com.withwon.domain.incident.Incident;
import com.withwon.domain.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerEvidenceUploadServiceTest {

    private IncidentRepository incidentRepository;
    private EvidenceFileRepository evidenceFileRepository;
    private EvidenceVersionRepository evidenceVersionRepository;
    private EvidenceStorage evidenceStorage;
    private CustomerEvidenceUploadService service;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        evidenceFileRepository = mock(EvidenceFileRepository.class);
        evidenceVersionRepository = mock(EvidenceVersionRepository.class);
        evidenceStorage = mock(EvidenceStorage.class);
        service = new CustomerEvidenceUploadService(
                incidentRepository, evidenceFileRepository, evidenceVersionRepository, evidenceStorage);
    }

    @Test
    void uploadsCustomerEvidenceToOwnedDraftIncident() {
        Incident incident = mock(Incident.class);
        when(incident.isDraft()).thenReturn(true);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));
        when(evidenceStorage.store(any())).thenReturn(
                new EvidenceStorage.StoredEvidence("evidence-key", "hash", 4L));
        when(evidenceFileRepository.save(any(EvidenceFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceVersionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var file = new MockMultipartFile("file", "id.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
        var uploaded = service.upload(7L, 11L, "ID_CARD", true, false, file);

        assertEquals("ID_CARD", uploaded.evidenceFile().getDocumentType());
        assertEquals("evidence-key", uploaded.evidenceVersion().getStorageKey());
        verify(evidenceStorage).store(file);
    }

    @Test
    void rejectsEvidenceForSubmittedIncident() {
        Incident incident = mock(Incident.class);
        when(incident.isDraft()).thenReturn(false);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));

        var file = new MockMultipartFile("file", "id.jpg", "image/jpeg", new byte[]{1});

        assertThrows(DraftUpdateNotAllowedException.class,
                () -> service.upload(7L, 11L, "ID_CARD", false, false, file));
        verify(evidenceStorage, never()).store(any());
    }

    @Test
    void rejectsUnsupportedFileType() {
        Incident incident = mock(Incident.class);
        when(incident.isDraft()).thenReturn(true);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));

        var file = new MockMultipartFile("file", "archive.zip", "application/zip", new byte[]{1});

        assertThrows(IllegalArgumentException.class,
                () -> service.upload(7L, 11L, "EVIDENCE", false, false, file));
        verify(evidenceStorage, never()).store(any());
    }

    @Test
    void rejectsIncidentNotOwnedByCustomer() {
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.empty());

        var file = new MockMultipartFile("file", "id.jpg", "image/jpeg", new byte[]{1});

        assertThrows(IncidentNotFoundException.class,
                () -> service.upload(7L, 11L, "ID_CARD", false, false, file));
    }

    @Test
    void uploadsAnyNumberOfEvidenceFilesAsOneBatch() {
        Incident incident = mock(Incident.class);
        when(incident.isDraft()).thenReturn(true);
        when(incidentRepository.findByIdAndCustomer_Id(11L, 7L)).thenReturn(Optional.of(incident));
        when(evidenceStorage.store(any())).thenReturn(
                new EvidenceStorage.StoredEvidence("evidence-key", "hash", 1L));
        when(evidenceFileRepository.save(any(EvidenceFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceVersionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "transcript-1.txt", "text/plain", new byte[]{1}),
                new MockMultipartFile("files", "transcript-2.txt", "text/plain", new byte[]{2}));

        var uploaded = service.uploadBatch(7L, 11L, "TRANSCRIPT", false, false, files);

        assertEquals(2, uploaded.size());
        verify(evidenceStorage).store(files.get(0));
        verify(evidenceStorage).store(files.get(1));
    }
}
